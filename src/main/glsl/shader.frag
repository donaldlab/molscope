#version 450

layout(location = 0) in float inRadiusCamera;
layout(location = 1) in float inZClip;
layout(location = 2) in vec2 inPosFramebuf;
layout(location = 3) in vec4 inColor;

layout(location = 0) out vec4 outColor;
layout (depth_less) out float gl_FragDepth; // apparently we need to use this exact name

#include "view.glsl"


float zCameraToDepth(float z) {
	return (z - zNearCamera)/(zFarCamera - zNearCamera);
}


void main() {

	// TODO: optimize calculations

	// NOTE: fragment shaders operate entirely in framebuffer space
	// In framebuffer and NDC/clip spaces, the y axis is down!

	// start in framebuffer space
	vec2 posAtomFramebuf = inPosFramebuf;
	vec2 posPixelFramebuf = gl_FragCoord.xy;

	// transform to NDC space
	vec2 posAtomNDC = vec2(posAtomFramebuf*2/windowSize - vec2(1));
	vec2 posPixelNDC = vec2(posPixelFramebuf*2/windowSize - vec2(1));

	// transform to camera space (where +y is up, and +z is away from camera)
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
		gl_FragDepth = 1;

	} else {

		// pixel is on or inside the sphere, update the camera pos
		// we only care about the -z side of the sphere, ie towards the camera
		posPixelCamera.z = zAtomCamera - dz;

		// calc the sphere normal
		vec3 normal = normalize(posPixelCamera - posAtomCamera);

		// apply very simple lambertian lighting
		const float ambient = 0.5;
		const vec3 toLightVec = normalize(vec3(1, 1, -1));
		float diffuse = dot(normal, toLightVec);
		vec3 color = (ambient + diffuse*0.7)*inColor.rgb;

		outColor = vec4(color, inColor.a);

		// calc pixel depth
		gl_FragDepth = zCameraToDepth(posPixelCamera.z);
	}
}
