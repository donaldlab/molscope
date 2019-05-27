
#ifndef _LIGHT_GLSL_
#define _LIGHT_GLSL_


#include "math.glsl"


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

vec4 light(vec4 color, vec3 normal) {
	vec3 rgb = pbrLambert(color.rgb, normal);
	return vec4(rgb, color.a);
}

vec4 showNormal(vec3 normal) {
	return vec4((normal.xy + vec2(1))/2, 1 - (normal.z + 1)/2, 1);
}

vec4 showZ(float zClip) {
	return vec4(vec3(1 - zClip), 1);
}


#endif // _LIGHT_GLSL
