#version 450

layout(location = 0) in vec3 inPosWorld;
layout(location = 1) in float inRadiusWorld;
layout(location = 2) in vec4 inColor;

layout(location = 1) out float outRadiusCamera;
layout(location = 2) out vec2 outRadiusClip;
layout(location = 3) out vec4 outColor;

#include "view.glsl"

/* atom coords:
	x in [11,16]  w=5
	y in [24,28]  w=4
	z in [23,27]  w=4
*/
const vec3 center = vec3(11+16, 24+28, 23+27)/2;
const vec3 translation = vec3(-13, -26, 0);


void main() {

	// NOTE: vertex shaders convert from world space posto clip space
	// via world -> camera -> NDC -> clip

	// TODO: optimize calculations

	vec3 posWorld = inPosWorld;

	// TEMP: apply rotation to vertices
	float cosa = cos(angle);
	float sina = sin(angle);
	mat3 rot = mat3(
		 cosa, 0.0, sina,
		  0.0, 1.0,  0.0,
		-sina, 0.0, cosa
	);
	posWorld = rot*(posWorld - center) + center;

	// TODO: simplify the arithmetic

	// transform into camera space
	// TODO: do proper view transformation
	vec3 posCamera = posWorld - center + vec3(0, 0, -20);

	// convert to NDC (normalized device coords) space:
	// ie   x in [-1,1]   y in [-1,1]   z in [0,1]
	// and a right-handed axis system: x+right, y+down, z+away
	// aka, do perspective projection
	vec2 viewSize = windowSize/magnification;
	float dx = 2*zNearCamera/viewSize.x/posCamera.z*posCamera.x;
	float dy = -2*zNearCamera/viewSize.y/posCamera.z*posCamera.y;
	float dz = (posCamera.z - zNearCamera)/(zFarCamera - zNearCamera);
	vec3 posNDC = vec3(dx, dy, dz);
	vec2 radiusNDC = vec2(
		2*zNearCamera/viewSize.x/posCamera.z*inRadiusWorld,
		2*zNearCamera/viewSize.y/posCamera.z*inRadiusWorld
	);

	// convert to clip space by lifting to homogeneous coords
	vec4 posClip = vec4(posNDC, 1);
	vec2 radiusClip = radiusNDC;

	// send outputs to next shader stages
	gl_Position = posClip;
	outRadiusCamera = inRadiusWorld;
	outRadiusClip = radiusClip;
	outColor = inColor;
}
