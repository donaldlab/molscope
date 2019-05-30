
#ifndef _LIGHT_GLSL_
#define _LIGHT_GLSL_


#include "math.glsl"


layout(binding = 1) uniform isampler3D inOcclusion;

layout(binding = 2, std140) uniform readonly restrict OcclusionField {
	// (with padding to align vec3s at 16-byte boundaries)
	ivec3 samples;
	int maxOcclusion;
	vec3 min; // in world space
	float pad2;
	vec3 max;
	float pad3;
} inOcclusionField;

layout(binding = 3, std140) uniform readonly restrict Settings {
	float lightingWeight;
	float ambientOcclusionWeight;
} inSettings;


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

	return float(texture(inOcclusion, uvw).r)/float(inOcclusionField.maxOcclusion);
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

	vec3 rgb = vec3(1, 1, 1);

	// apply lighting if neeed
	if (inSettings.lightingWeight > 0) {
		rgb = mix(rgb, pbrLambert(color.rgb, normalCamera), inSettings.lightingWeight);
	}

	// apply ambient occlusion if needed
	if (inSettings.ambientOcclusionWeight > 0) {
		rgb *= 1 - inSettings.ambientOcclusionWeight*sampleOcclusion(worldToOcclusionField(cameraToWorld(posCamera)));
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
