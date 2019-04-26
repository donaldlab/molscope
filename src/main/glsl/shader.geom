#version 450

layout(points) in;
layout(triangle_strip, max_vertices = 4) out;

layout(location = 1) in float inRadiusCamera[];
layout(location = 2) in vec2 inRadiusClip[];
layout(location = 3) in vec4 inColor[];


layout(location = 0) out float outRadiusCamera;
layout(location = 1) out float outZClip;
layout(location = 2) out vec2 outPosFramebuf;
layout(location = 3) out vec4 outColor;

#include "view.glsl"


void emitVertex(vec4 posClip, vec2 deltaClip, vec2 posFramebuf) {
	gl_Position = posClip + vec4(deltaClip, 0, 0);
	outRadiusCamera = inRadiusCamera[0];
	outZClip = posClip.z;
	outPosFramebuf = posFramebuf;
	outColor = inColor[0];
	EmitVertex();
}

void main() {

	// NOTE: geometry shaders operate entirely in clip space

	// transform the position from clip space into framebuffer space
	vec4 posClip = gl_in[0].gl_Position;
	vec2 posFramebuf = (posClip.xy + vec2(1))*windowSize/2;

	// emit the four vertices of a billboard quad, in triangle strip order, with ccw facing
	emitVertex(posClip, vec2(+inRadiusClip[0].x, +inRadiusClip[0].y), posFramebuf);
	emitVertex(posClip, vec2(+inRadiusClip[0].x, -inRadiusClip[0].y), posFramebuf);
	emitVertex(posClip, vec2(-inRadiusClip[0].x, +inRadiusClip[0].y), posFramebuf);
	emitVertex(posClip, vec2(-inRadiusClip[0].x, -inRadiusClip[0].y), posFramebuf);
	EndPrimitive();
}
