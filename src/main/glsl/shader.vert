#version 450

layout(location = 0) in vec3 inPosWorld;
layout(location = 1) in float inRadiusWorld;
layout(location = 2) in vec4 inColor;

layout(location = 1) out float outRadiusCamera;
layout(location = 2) out vec2 outRadiusClip;
layout(location = 3) out vec4 outColor;

#include "view.glsl"


void main() {

	// NOTE: vertex shaders convert from world space posto clip space
	// via world -> camera -> NDC -> clip

	// TODO: optimize calculations

	// transform into camera space
	vec3 posCamera = inPosWorld - cameraWorld;
	posCamera = vec3(
		dot(cameraSide, posCamera),
		dot(cameraUp, posCamera),
		dot(cameraLook, posCamera)
	);

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
