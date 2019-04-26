#version 450

layout(location = 0) in float inRadiusCamera;
layout(location = 1) in float inZClip;
layout(location = 2) in vec2 inPosFramebuf;
layout(location = 3) in vec4 inColor;

layout(location = 0) out vec4 outColor;

layout(binding = 0, r32ui) uniform restrict uimage2D depthBuffer;

#include "view.glsl"


const uint MaxUint = 4294967295;
uint zCameraToDepth(float z) {
	float n = (z - zNearCamera)/(zFarCamera - zNearCamera);
	// TODO: bias depth buffer so it has more precision closer to the near plane?
	//n = sqrt(n);
	return uint(n*MaxUint);
}


void main() {

	// TODO: optimize calculations

	// NOTE: fragment shaders operate entirely in framebuffer space
	// in fragment and NDC/clip spaces, the y axis is down

	// start in framebuffer space
	vec2 posAtomFramebuf = inPosFramebuf;
	vec2 posPixelFramebuf = gl_FragCoord.xy;

	// transform to NDC space
	vec2 posAtomNDC = vec2(posAtomFramebuf*2/windowSize - vec2(1));
	vec2 posPixelNDC = vec2(posPixelFramebuf*2/windowSize - vec2(1));

	// transform to camera space (where y axis is up)
	vec2 viewSize = windowSize/magnification;
	float zAtomCamera = inZClip*(zFarCamera - zNearCamera) + zNearCamera;
	vec3 posAtomCamera = vec3(
		posAtomNDC.x*zAtomCamera*viewSize.x/2/zNearCamera,
		-posAtomNDC.y*zAtomCamera*viewSize.y/2/zNearCamera,
		zAtomCamera
	);
	vec3 posPixelCamera = vec3(
		posPixelNDC.x*zAtomCamera*viewSize.x/2/zNearCamera,
		-posPixelNDC.y*zAtomCamera*viewSize.y/2/zNearCamera,
		zAtomCamera
	);

	// compute the pixel z pos in camera space
	float dx = posPixelCamera.x - posAtomCamera.x;
	float dy = posPixelCamera.y - posAtomCamera.y;
	float dz = sqrt(inRadiusCamera*inRadiusCamera - dx*dx - dy*dy);
	if (isnan(dz)) {

		// pixel is outside the sphere
		outColor = vec4(0);

	} else {

		// pixel is on or inside the sphere, update the camera pos
		// we only care about the +z side of the sphere, ie towards the camera
		posPixelCamera.z = zAtomCamera + dz;

		// depth buffer test
		uint depth = zCameraToDepth(posPixelCamera.z);
		uint oldDepth = imageAtomicMin(depthBuffer, ivec2(gl_FragCoord.xy), depth);
		if (depth >= oldDepth) {

			// failed depth test
			outColor = vec4(0);

		} else {

			// passed depth test

			// calc the sphere normal
			vec3 normal = normalize(posPixelCamera - posAtomCamera);

			// apply very simple lambertian lighting
			const float ambient = 0.5;
			const vec3 toLightVec = normalize(vec3(1, 1, 1));
			float diffuse = dot(normal, toLightVec);
			vec3 color = (ambient + diffuse*0.7)*inColor.rgb;

			outColor = vec4(color, 1);
		}
	}
}
