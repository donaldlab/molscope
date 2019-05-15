#version 450

layout(lines) in; // ??? gl_in[]
layout(triangle_strip, max_vertices = 4) out; // vec4 gl_Position

layout(location = 0) in vec3 inPosCamera[2];
layout(location = 1) in float inRadiusCamera[2];
layout(location = 2) in vec2 inRadiusClip[2];
layout(location = 3) in vec4 inColor[2];

layout(location = 0) out vec3 outPosCamera[2];
layout(location = 2) out float outRadiusCamera[2];
layout(location = 4) out vec4 outColor[2];

#include "view.glsl"


void emitVertex(vec4 posClip) {
	gl_Position = posClip;
	outPosCamera = inPosCamera;
	outRadiusCamera = inRadiusCamera;
	outColor = inColor;
	EmitVertex();
}

void main() {

	// NOTE: geometry shaders operate entirely in clip space

	// get the vertex position in a more convenient format
	vec4[] posClip = {
		gl_in[0].gl_Position,
		gl_in[1].gl_Position
	};

	// get the line vector
	vec2 dir = normalize(posClip[1].xy - posClip[0].xy);

	// do a 90 degree ccw rotation to get a perpendicular direction
	vec2 pdir = vec2(-dir[1], dir[0]);

	// TODO: could make this tighter by doing some elliptical math
	// dunno if it's worth it though
	float r = max(
		max(inRadiusClip[0].x, inRadiusClip[1].x),
		max(inRadiusClip[0].y, inRadiusClip[1].y)
	);

	// emit the four vertices of a billboard quad, in triangle strip order, with ccw facing
	emitVertex(posClip[0] + vec4(0 - dir*r - pdir*r, 0, 0));
	emitVertex(posClip[0] + vec4(0 - dir*r + pdir*r, 0, 0));
	emitVertex(posClip[1] + vec4(0 + dir*r - pdir*r, 0, 0));
	emitVertex(posClip[1] + vec4(0 + dir*r + pdir*r, 0, 0));

	EndPrimitive();
}
