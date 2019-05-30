
#ifndef _LIGHT_GLSL_
#define _LIGHT_GLSL_


#include "math.glsl"


layout(binding = 1) uniform sampler3D inOcclusionXY;
layout(binding = 2) uniform sampler3D inOcclusionZ;

layout(binding = 3, std140) uniform readonly restrict OcclusionField {
	// (with padding to align vec3s at 16-byte boundaries)
	ivec3 samples;
	int pad1;
	vec3 min; // in world space
	float pad2;
	vec3 max;
	float pad3;
} inOcclusionField;


const vec3 toLight = normalize(vec3(1, 1, -1));

vec3 pbrLambert(vec3 albedo, vec3 normal) {

	vec3 a = albedo;
	vec3 n = normal;
	vec3 l = toLight;

	// see math/lighting-lambert.wxm for derivation
	float a1 = 1/PI;
	float a2 = -2*PI*l.z*abs(n.z);
	return (a1*a*(a2+2*PI*l.y*n.y+2*PI*l.x*n.x+3*PI))/6;
}

float sampleOcclusion(vec3 posField) {

	// convert the position to texture coords
	// scale the field coords to the centers of the pixels
	vec3 pixelSize = vec3(1)/vec3(inOcclusionField.samples);
	vec3 uvw = posField*(1 - pixelSize)/(vec3(inOcclusionField.samples) - vec3(1)) + pixelSize/2;

	vec4 occlusionXY = texture(inOcclusionXY, uvw);
	vec2 occlusionZ = texture(inOcclusionZ, uvw).rg;

	/* TEMP
	// get the occlusion for this direction
	return 0
		+ abs(n.x)*occlusionXY[n.x < 0 ? 0 : 1]
		+ abs(n.y)*occlusionXY[n.y < 0 ? 2 : 3]
		+ abs(n.z)*occlusionZ[n.z < 0 ? 0 : 1];
	*/

	/* TEMP
	float ambient = 6
		- occlusionXY[0]
		- occlusionXY[1]
		- occlusionXY[2]
		- occlusionXY[3]
		- occlusionZ[0]
		- occlusionZ[1];
	//return clamp(ambient, 0, 1);
	return ambient/6;
	*/
	return occlusionZ[0];
}

float nearestOcclusion(vec3 posField) {
	return sampleOcclusion(vec3(ivec3(posField + vec3(0.5))));
}

vec3 worldToOcclusionField(vec3 posWorld) {
	vec3 posField = posWorld - inOcclusionField.min;
	posField *= ivec3(inOcclusionField.samples) - ivec3(1);
	posField /= inOcclusionField.max - inOcclusionField.min;
	return posField;
}

vec4 light(vec4 color, vec3 posCamera, vec3 normalCamera) {

	// apply lighting
	vec3 rgb = pbrLambert(color.rgb, normalCamera);

	// apply ambient occlusion if needed
	// TODO: NEXTTIME: make a render settings buffer for the weight
	const float ambientOcclusionWeight = 0; // TEMP
	if (ambientOcclusionWeight > 0) {
		rgb *= 1 - ambientOcclusionWeight*sampleOcclusion(worldToOcclusionField(cameraToWorld(posCamera)));
	}
	
	return vec4(rgb, color.a);
}

vec4 showNormal(vec3 normal) {
	return vec4((normal.xy + vec2(1))/2, 1 - (normal.z + 1)/2, 1);
}

vec4 showZ(float zClip) {
	return vec4(vec3(1 - zClip), 1);
}


#endif // _LIGHT_GLSL
