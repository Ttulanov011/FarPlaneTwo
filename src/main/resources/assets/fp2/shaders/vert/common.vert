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
// INPUTS
//
//

/*layout(std430, binding = 3) readonly buffer POSITIONS {
    ivec4 positions[];
} in_positions;
#define tile_position (in_positions.positions[gl_DrawID])*/

//
//
// OUTPUTS
//
//

out VS_OUT {
    vec3 pos;
    vec2 light;

    flat vec3 color;
    flat vec3 base_pos;
    flat int state;
} vs_out;

//
//
// CONSTANTS
//
//

#if defined(USE_DEBUG_COLORS_DISTANCE) || defined(USE_DEBUG_COLORS_POSITIONS)
const vec3[16] DEBUG_COLORS = vec3[](
vec3(0., 1., 0.), vec3(1., 1., 0.), vec3(1., 0., 0.), vec3(0., 0., 1.),
vec3(1., 0., 1.), vec3(0., 1., 1.), vec3(.5), vec3(1.),
vec3(.5, 1., 0.), vec3(0., 1., .5), vec3(1., .5, 0.), vec3(1., 0., .5),
vec3(0., .5, 1.), vec3(.5, 0., 1.), vec3(5., 1., .5), vec3(5., 1., .5)
);
#endif

//
//
// UTILITIES
//
//

vec3 computeVertexColor(vec3 va_color, ivec4 tile_position) {
#if defined(USE_DEBUG_COLORS_DISTANCE)
    return DEBUG_COLORS[tile_position.w];
#elif defined(USE_DEBUG_COLORS_POSITIONS)
    ivec4 i = (tile_position & 1) << ivec4(3, 2, 1, 0);
    return DEBUG_COLORS[(i.x | i.y) | (i.z | i.w)];
#else
    return va_color;
#endif
}
