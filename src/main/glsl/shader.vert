#version 450

layout(location = 0) in vec3 inPosition;
layout(location = 1) in float inRadius;
layout(location = 2) in vec4 inColor;

layout(location = 0) out vec3 outPosWorld;
layout(location = 1) out float outRadiusWorld;
layout(location = 2) out vec2 outRadiusClip;
layout(location = 3) out vec4 outColor;

layout(binding = 1, std140) uniform restrict readonly ViewBuf {
	float angle;
};

// TODO: make uniform buf for global transformations
const vec3 windowSize = vec3(640, 480, 10000);
const vec3 translation = vec3(-13, -26, 0);
const vec3 scale = 160.0/windowSize/2;

const float PI = 3.141592653589793; // probably more than enough precision

void main() {

	vec3 pos = inPosition;

	// TEMP: apply rotation to vertices
	vec3 center = vec3(13, 24, 24);
	float cosa = cos(angle);
	float sina = sin(angle);
	mat3 rot = mat3(
		 cosa, 0.0, sina,
		  0.0, 1.0,  0.0,
		-sina, 0.0, cosa
	);
	pos = rot*(pos - center) + center;

	// convert to clip space
	vec3 posClip = (pos + translation)*scale;

	outPosWorld = pos;
	outRadiusWorld = inRadius;
	gl_Position = vec4(posClip, 1.0);
	outRadiusClip = outRadiusWorld*scale.xy;
	outColor = inColor;
}
