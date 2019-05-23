#version 450

layout(binding = 0, std140) buffer restrict readonly Cursor {
	int isActive;
	// 4 bytes pad
	ivec2 pos;
	ivec2 index;
} inCursor;

layout(binding = 1, rgba8) uniform restrict readonly image2D inColor;
layout(binding = 2, rg32i) uniform restrict readonly iimage2D inIndex;

layout(location = 0) out vec4 outColor;

void main() {

	ivec2 p = ivec2(gl_FragCoord.xy);

	// get the color
	vec4 color = imageLoad(inColor, p);

	// apply cursor effects if needed
	if (inCursor.isActive == 1 && inCursor.index.x >= 0) {

		// compare to this pixel's index
		ivec2 pixelIndex = imageLoad(inIndex, p).rg;
		if (inCursor.index == pixelIndex) {

			// make the color stand out a bit more
			color = vec4(color.rgb*1.8, color.a);

			// TODO: fancier effects?
		}
	}

	outColor = color;
}
