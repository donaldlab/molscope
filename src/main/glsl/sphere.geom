#version 450

layout(points) in; // ??? gl_in[]
layout(triangle_strip, max_vertices = 4) out; // vec4 gl_Position

layout(location = 0) in vec3 inPosCamera[1];
layout(location = 1) in float inRadiusCamera[1];
layout(location = 2) in vec2 inRadiusClip[1];
layout(location = 3) in vec4 inColor[1];

layout(location = 0) out vec3 outPosCamera;
layout(location = 1) out float outRadiusCamera;
layout(location = 2) out vec4 outColor;

#include "view.glsl"


void emitVertex(float deltaClipX, float deltaClipY) {
	vec4 posClip = gl_in[0].gl_Position;
	gl_Position = posClip + vec4(deltaClipX, deltaClipY, 0, 0);
	outPosCamera = inPosCamera[0];
	outRadiusCamera = inRadiusCamera[0];
	outColor = inColor[0];
	EmitVertex();
}

void main() {

	// NOTE: geometry shaders operate entirely in clip space

	// emit the four vertices of a billboard quad, in triangle strip order, with ccw facing
	vec2 radiusClip = inRadiusClip[0];
	emitVertex(+radiusClip.x, +radiusClip.y);
	emitVertex(+radiusClip.x, -radiusClip.y);
	emitVertex(-radiusClip.x, +radiusClip.y);
	emitVertex(-radiusClip.x, -radiusClip.y);
	EndPrimitive();
}
