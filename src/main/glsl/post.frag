#version 450

layout(binding = 0, std140) uniform restrict readonly Hover {
	int isHovering;
	// 4 bytes offset
	ivec2 pos;
} hover;

layout(binding = 1, rgba8) uniform restrict readonly image2D inColor;
layout(binding = 2, r32i) uniform restrict readonly iimage2D inIndex;

layout(location = 0) out vec4 outColor;

void main() {

	ivec2 p = ivec2(gl_FragCoord.xy);

	// get the color
	vec4 color = imageLoad(inColor, p);

	// apply hover effects if needed
	if (hover.isHovering == 1) {

		// get the hovered index
		int hoverIndex = imageLoad(inIndex, hover.pos).r;
		if (hoverIndex >= 0) {

			// compare to this pixel's index
			int pixelIndex = imageLoad(inIndex, p).r;
			if (hoverIndex == pixelIndex) {

				// make the color stand out a bit more
				color = vec4(color.rgb*1.8, color.a);

				// TODO: fancier effects?
			}
		}
	}

	outColor = color;
}
