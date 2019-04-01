#version 450

layout(points) in;
layout(triangle_strip, max_vertices = 4) out;

layout(location = 0) in vec3 inPosWorld[];
layout(location = 1) in float inRadiusWorld[];
layout(location = 2) in vec2 inRadiusClip[];
layout(location = 3) in vec4 inColor[];

layout(location = 0) out vec3 outPosWorld;
layout(location = 1) out vec3 outPosFramebuf;
layout(location = 2) out float outRadiusFramebuf;
layout(location = 3) out vec4 outColor;

// TODO: make uniform buf for global transformations
const vec3 windowSize = vec3(640, 480, 10000);

void emitVertex(vec3 posFramebuf, vec2 radiusFramebuf, vec4 posClip, vec2 deltaClip) {
	outPosWorld = inPosWorld[0];
	outPosFramebuf = posFramebuf;
	outRadiusFramebuf = radiusFramebuf.x;
	outColor = inColor[0];
	gl_Position = posClip + vec4(deltaClip, 0, 0);
	EmitVertex();
}

void main() {

	// transform the position
	vec4 posClip = gl_in[0].gl_Position;
	vec3 posFramebuf = (posClip.xyz + vec3(1))*windowSize/2;

	// transform the radius
	vec2 radiusClip = inRadiusClip[0];
	vec2 radiusFramebuf = radiusClip*windowSize.xy/2;

	// redo the clip radius with 2 extra pixels for anti-aliasing
	radiusClip = (radiusFramebuf + vec2(2))*2/windowSize.xy;

	// emit the four vertices of a billboard quad, in triangle strip order, with ccw facing
	emitVertex(posFramebuf, radiusFramebuf, posClip, vec2(+radiusClip.x, +radiusClip.y));
	emitVertex(posFramebuf, radiusFramebuf, posClip, vec2(+radiusClip.x, -radiusClip.y));
	emitVertex(posFramebuf, radiusFramebuf, posClip, vec2(-radiusClip.x, +radiusClip.y));
	emitVertex(posFramebuf, radiusFramebuf, posClip, vec2(-radiusClip.x, -radiusClip.y));
	EndPrimitive();
}
