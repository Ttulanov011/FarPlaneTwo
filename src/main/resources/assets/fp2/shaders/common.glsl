/*
 * Adapted from The MIT License (MIT)
 *
 * Copyright (c) 2020-$today.year DaPorkchop_
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

//
//
// MACROS
//
//

//colors terrain based on its distance to the camera
//#define USE_DEBUG_COLORS_DISTANCE

//colors terrain based on its tile position
//#define USE_DEBUG_COLORS_POSITIONS

//colors terrain based on its face normal vector
//#define USE_DEBUG_COLORS_FACE_NORMAL

#if defined(USE_DEBUG_COLORS_DISTANCE) || defined(USE_DEBUG_COLORS_POSITIONS) || defined(USE_DEBUG_COLORS_FACE_NORMAL)
#define USE_DEBUG_COLORS
#endif

#define FOG_LINEAR (9729)
#define FOG_EXP (2048)
#define FOG_EXP2 (2049)

#define T_SHIFT (4)
#define T_MASK ((1 << T_SHIFT) - 1)
#define T_VOXELS (1 << T_SHIFT)
#define T_VERTS (T_VOXELS + 1)

#define RENDER_PASS_COUNT (3)

//
//
// UNIFORMS
//
//

//OpenGL state

struct GlCamera {
    mat4 modelviewprojection;

    ivec3 position_floor;
    vec3 position_fract;
};

struct GlFog {
    vec4 color;

    float density;
    float start;
    float end;
    float scale;

    int mode;
};

layout(std140, binding = 0) uniform GLSTATE {
    GlCamera camera;
    GlFog fog;
} glState;

//FP2 state

struct FP2_View {
    int renderDistance;
    int maxLevels;
    int levelCutoffDistance;
    float transitionStart;
    float transitionEnd;
};

layout(std140, binding = 1) uniform FP2_STATE {
    FP2_View view;
} fp2_state;

//
//
// BUFFERS
//
//

//Texture UVs

layout(std430, binding = 0) readonly buffer QUAD_LISTS {
    ivec2 quad_lists[];
};

struct BakedQuad {
    float minU; //written out to avoid padding
    float minV;
    float maxU;
    float maxV;
    float tintFactor;
};

layout(std430, binding = 1) readonly buffer QUAD_DATA {
    BakedQuad quad_data[];
};

#if defined(LEVEL_0)
//Vanilla renderability index

layout(std430, binding = 6) readonly buffer VANILLA_RENDERABILITY {
    ivec3 offset;
    ivec3 size;

    int _padding; //std430 layout is weird lol

    uint flags[];
} vanilla_renderability_state;
#endif

//
//
// UTILITIES
//
//

// color unpacking

vec4 fromARGB(uint argb)   {
    return vec4(uvec4(argb) >> uvec4(16, 8, 0, 24) & uint(0xFF)) / 255.;
}

vec4 fromARGB(int argb)   {
    return fromARGB(uint(argb));
}

vec4 fromRGB(uint rgb)   {
    return fromARGB(uint(0xFF000000) | rgb);
}

vec4 fromRGB(int rgb)   {
    return fromRGB(uint(rgb));
}

// vertex transformation

vec4 cameraTransform(vec4 point) {
    return glState.camera.modelviewprojection * point;
}

vec4 cameraTransform(vec3 point)   {
    return cameraTransform(vec4(point, 1.));
}

// lighting

float diffuseLight(vec3 normal) {
    //compute all component values in parallel (possibly more likely to be vectorized better)
    vec3 values = (normal * normal) * (normal * vec3(0., .25, 0.) + vec3(.6, .75, .8));

    //add them together and prevent them from getting too high
    return min(values.x + values.y + values.z, 1.);

    // equivalent code:
    //return min(normal.x * normal.x * .6 + normal.y * normal.y * ((3. + normal.y) / 4.) + normal.z * normal.z * .8, 1.);
}

// vector math

int normalToFaceIndex(vec3 normal)  {
    vec3 n = abs(normal);

    //component-wise mask. one lane is set (the lane whose component has the greatest absolute value)
    ivec3 axisMask = -ivec3(greaterThan(n, max(n.yxx, n.zzy)));

    //components set to 0 if the corresponding component in the normal vector is negative, 1 otherwise
    ivec3 positive = ivec3(greaterThanEqual(normal, vec3(0.)));

    //the base offset to apply per component
    const ivec3 base = ivec3(4, 0, 2);

    //contains the final output value for each component, or 0 if the component isn't the greatest
    ivec3 values = (base + positive) & axisMask;

    //the maximum component value will be the only one that wasn't masked to zero
    return max(values.x, max(values.y, values.z));

    // equivalent code:
    /*if (n.y > n.x && n.y > n.z)  {
        return normal.y < 0. ? 0 : 1;
    } else if (n.z > n.x && n.z > n.y) {
        return normal.z < 0. ? 2 : 3;
    } else {
        return normal.x < 0. ? 4 : 5;
    }*/
}

#if defined(LEVEL_0)
// vanilla renderability tests

bool isVanillaRenderableLevel0(in ivec3 chunkPos) {
    ivec3 tableOffset = vanilla_renderability_state.offset;
    ivec3 tableSize = vanilla_renderability_state.size;

    //offset the given chunk position by the table offset
    ivec3 offsetPos = chunkPos + tableOffset;

    //clamp coordinates to the table size (this is safe because the edges are always false)
    offsetPos = min(max(offsetPos, 0), tableSize - 1);

    //compute bit index in the table
    int idx = (offsetPos.x * tableSize.y + offsetPos.y) * tableSize.z + offsetPos.z;

    //extract the bit at the given index
    return (vanilla_renderability_state.flags[idx >> 5] & (1 << idx)) != 0;
}
#endif
