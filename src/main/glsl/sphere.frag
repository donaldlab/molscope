#version 450

layout(location = 0) in vec3 inPosCamera;
layout(location = 1) in float inRadiusCamera;
layout(location = 2) in vec4 inColor;

layout(location = 0) out vec4 outColor;
layout (depth_less) out; // float gl_FragDepth

#include "view.glsl"
#include "light.glsl"


void main() {

	// transform from framebuf to camera space
	vec3 posCenterCamera = inPosCamera;
	vec3 posPixelCamera = framebufToCamera(gl_FragCoord);

	// compute the pixel z pos on the sphere, in camera space
	float dx = posPixelCamera.x - posCenterCamera.x;
	float dy = posPixelCamera.y - posCenterCamera.y;
	float dz = sqrt(inRadiusCamera*inRadiusCamera - dx*dx - dy*dy);
	if (isnan(dz)) {

		// pixel is outside the sphere
		outColor = vec4(0);
		gl_FragDepth = 1;

	} else {

		// pixel is on or inside the sphere, update the camera pos
		// we only care about the -z side of the sphere, ie towards the camera
		posPixelCamera.z = posCenterCamera.z - dz;

		// calc the sphere normal
		vec3 normal = normalize(posPixelCamera - posCenterCamera);

		// apply very simple lambertian lighting
		outColor = vec4(lambertian(inColor.rgb, normal), inColor.a);

		// calc pixel depth
		gl_FragDepth = cameraZToClip(posPixelCamera.z);
	}
}
