/*
 * Adapted from The MIT License (MIT)
 *
 * Copyright (c) 2020-2021 DaPorkchop_
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * Any persons and/or organizations using this software must include the above copyright notice and this permission notice,
 * provide sufficient credit to the original authors of the project (IE: DaPorkchop_), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package net.daporkchop.fp2.util.threading.scheduler;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import net.daporkchop.fp2.util.threading.ThreadingHelper;
import net.daporkchop.fp2.util.threading.workergroup.WorkerGroupBuilder;
import net.daporkchop.fp2.util.threading.workergroup.WorldWorkerGroup;
import net.daporkchop.lib.common.misc.string.PStrings;
import net.daporkchop.lib.common.ref.Ref;
import net.daporkchop.lib.common.ref.ThreadRef;
import net.daporkchop.lib.unsafe.PUnsafe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.Math.*;
import static net.daporkchop.lib.common.util.PValidation.*;

/**
 * @author DaPorkchop_
 */
public class SharedFutureScheduler<P, V> implements Scheduler<P, V>, Runnable {
    protected static final long TASK_DEPENDENCIES_OFFSET = PUnsafe.pork_getOffset(SharedFutureScheduler.Task.class, "dependencies");

    protected final Map<P, Task> tasks = new ConcurrentHashMap<>();
    protected final BlockingQueue<Task> queue = this.createTaskQueue();

    protected final Ref<Deque<Task>> recursionStack = ThreadRef.late(this.recursionStackFactory());

    protected final Function<P, V> function;

    protected final WorldWorkerGroup group;
    protected volatile boolean running = true;

    public SharedFutureScheduler(@NonNull Function<Scheduler<P, V>, Function<P, V>> functionFactory, @NonNull WorkerGroupBuilder builder) {
        this.function = functionFactory.apply(this);

        this.group = builder.build(this);
    }

    protected Supplier<Deque<Task>> recursionStackFactory() {
        return ArrayDeque::new;
    }

    protected BlockingQueue<Task> createTaskQueue() {
        return new LinkedBlockingQueue<>();
    }

    protected Task createTask(@NonNull P param) {
        return new Task(param);
    }

    protected void enqueue(@NonNull Task task) {
        checkState(SharedFutureScheduler.this.queue.add(task));
    }

    protected void unqueue(@NonNull Task task) {
        //do nothing - the task will be cancelled, and the worker threads will remove it from the queue once we get to it.
        //  we don't want to actually remove it from the queue, since LinkedBlockingQueue#remove(Object) is O(n).
    }

    @Override
    public CompletableFuture<V> schedule(@NonNull P param) {
        return this.retainTask(param);
    }

    @Override
    public void close() {
        //notify workers that we're shutting down
        this.running = false;

        //wait until all the workers have exited
        this.group.close();
    }

    protected Task retainTask(@NonNull P _param) {
        class State implements BiFunction<P, Task, Task> {
            Task task;

            @Override
            public Task apply(@NonNull P param, Task task) {
                if (task == null) { //task doesn't exist, create new one
                    task = SharedFutureScheduler.this.createTask(param);

                    //add task to execution queue
                    SharedFutureScheduler.this.enqueue(task);
                } else if (task.refCnt < 0) { //task is currently being executed, we don't need to retain it
                } else { //retain existing task
                    task.refCnt = incrementExact(task.refCnt);
                }

                this.task = task;
                return task;
            }
        }

        State state = new State();
        this.tasks.compute(_param, state);
        return state.task;
    }

    protected boolean releaseTask(@NonNull Task expectedTask) {
        class State implements BiFunction<P, Task, Task> {
            boolean released;

            @Override
            public Task apply(@NonNull P param, Task task) {
                if (task != expectedTask //tasks don't match, do nothing
                    || task.refCnt < 0) { //task is currently being executed, we can't release it
                    this.released = false;
                    return task;
                } else {
                    this.released = true;
                }

                if (--task.refCnt != 0) { //reference count is non-zero, the task is still live
                    return task;
                } else { //reference count reached zero! cancel the task, unqueue it and remove it from the map
                    task.cancel(false);
                    SharedFutureScheduler.this.unqueue(task);
                    return null;
                }
            }
        }

        State state = new State();
        this.tasks.compute(expectedTask.param, state);
        return state.released;
    }

    protected boolean beginTask(@NonNull Task expectedTask) {
        class State implements BiFunction<P, Task, Task> {
            boolean started;

            @Override
            public Task apply(@NonNull P param, Task task) {
                if (task != expectedTask //tasks don't match, do nothing
                    || task.refCnt < 0) { //task is currently being executed, we can't start executing it
                    this.started = false;
                } else {
                    //set reference count to -1 to indicate that it's started execution
                    task.refCnt = -1;
                    this.started = true;

                    SharedFutureScheduler.this.unqueue(task);
                }
                return task;
            }
        }

        State state = new State();
        this.tasks.compute(expectedTask.param, state);
        return state.started;
    }

    protected void deleteTask(@NonNull Task task) {
        checkState(this.tasks.remove(task.param, task), "unable to delete task for %s?!?", task.param);
    }

    protected void pollAndExecuteSingleTask() {
        if (!this.running) {
            throw new SchedulerClosedError();
        }

        Task task = this.pollSingleTask();
        if (task == null //queue is empty
            || !this.beginTask(task)) { //we lost the "race" to begin executing the task
            return;
        }

        this.executeTask(task);
    }

    @SneakyThrows(InterruptedException.class)
    protected Task pollSingleTask() {
        //poll the queue, but don't wait indefinitely because we need to be able to exit if the executor stops running.
        // we don't want to use interrupts because they can cause unwanted side-effects (such as closing NIO channels).
        return this.queue.poll(1L, TimeUnit.SECONDS);
    }

    protected void executeTask(@NonNull Task task) {
        if (!this.running) {
            throw new SchedulerClosedError();
        }

        Deque<Task> recursionStack = this.recursionStack.get();
        recursionStack.push(task);

        try { //execute the task and complete future accordingly
            task.complete(this.function.apply(task.param));
        } catch (SchedulerClosedError e) { //catch and rethrow this separately to prevent it from being used to complete the future
            task.cancel(true); //cancel the future to make sure it has a return value
            throw e;
        } catch (Throwable t) {
            task.completeExceptionally(t);
            ThreadingHelper.handle(this.group.world(), t);
        } finally { //the task's been executed, remove it from the map
            this.deleteTask(task);

            checkState(task == recursionStack.pop());
        }
    }

    /**
     * @deprecated internal API, do not touch!
     */
    @Override
    @Deprecated
    public void run() {
        try {
            while (true) {
                this.pollAndExecuteSingleTask();
            }
        } catch (SchedulerClosedError e) {
            //swallow error and exit quietly
        }
    }

    protected void awaitJoin(@NonNull Task task) {
        //we don't want to actually BLOCK the worker thread while waiting for a task to complete! that would be stupid, let's make it do some actual work instead.

        //it would be best if we could start by executing the task we want to wait for, so let's start by trying to begin it now
        if (this.beginTask(task)) { //we won the race to execute the task! actually execute it and return immediately, nothing else remains to be done
            this.executeTask(task);
        } else { //keep ourselves occupied by doing other tasks until the one we're waiting for is completed
            while (!task.isDone()) {
                this.pollAndExecuteSingleTask();
            }
        }
    }

    @Override
    public List<V> scatterGather(@NonNull List<P> params) {
        List<Task> tasks = this.scatter(params);

        Deque<Task> recursionStack = this.recursionStack.get();
        Task parent = recursionStack.peek();
        if (parent != null) { //this is a recursive task, so we need to make sure the parent task is aware that it's resulted in child tasks being spawned
            if (!PUnsafe.compareAndSwapObject(parent, TASK_DEPENDENCIES_OFFSET, null, tasks)) { //there may only be one active scatter/gather per task at a time
                tasks.forEach(this::releaseTask);
                throw new IllegalStateException(PStrings.fastFormat("task for %s has already started recursion!", parent.param));
            }

            try {
                return this.gather(tasks);
            } finally {
                if (PUnsafe.compareAndSwapObject(parent, TASK_DEPENDENCIES_OFFSET, tasks, null)) { //don't release dependencies if they've already been released from
                    //  another thread (due to this task's cancellation)
                    tasks.forEach(this::releaseTask);
                }
            }
        } else { //top-level task, do a simple scatter/gather
            try {
                return this.gather(tasks);
            } finally {
                tasks.forEach(this::releaseTask);
            }
        }
    }

    protected List<Task> scatter(@NonNull List<P> params) {
        List<Task> tasks = new ArrayList<>(params.size());
        for (P param : params) {
            tasks.add(this.retainTask(param));
        }
        return tasks;
    }

    protected List<V> gather(@NonNull List<Task> tasks) {
        //race to complete each task
        for (Task task : tasks) {
            if (this.beginTask(task)) {
                this.executeTask(task);
            }
        }

        //join all the tasks, which will block if necessary
        List<V> values = new ArrayList<>(tasks.size());
        for (SharedFutureScheduler<P, V>.Task task : tasks) {
            values.add(task.join());
        }
        return values;
    }

    /**
     * Thrown when the scheduler has closed in order to immediately terminate worker threads.
     *
     * @author DaPorkchop_
     */
    protected static class SchedulerClosedError extends Error {
    }

    /**
     * @author DaPorkchop_
     */
    @RequiredArgsConstructor
    protected class Task extends CompletableFuture<V> {
        @NonNull
        protected final P param;

        protected int refCnt = 1;

        //list of tasks whose results are required for the successful execution of the current task
        protected volatile Task[] dependencies = null;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return SharedFutureScheduler.this.releaseTask(this);
        }

        @Override
        public V join() {
            if (SharedFutureScheduler.this.group.threads().contains(Thread.currentThread())) {
                //we're on a worker thread, which means this task is being waited on recursively!
                //  let's steal work from the execution queue until this task is completed.
                SharedFutureScheduler.this.awaitJoin(this);
                return super.join();
            } else { //not recursive, block normally
                return ThreadingHelper.managedBlock(this);
            }
        }

        @Override
        public String toString() {
            return this.param.toString();
        }
    }
}
