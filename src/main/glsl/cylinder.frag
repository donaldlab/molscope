#version 450

layout(location = 0) flat in vec3 inPosCamera[2];
layout(location = 2) flat in float inRadiusCamera[2];
layout(location = 4) flat in vec4 inColor[2];
layout(location = 6) flat in int inIndex[2];

layout(location = 0) out vec4 outColor;
layout(location = 1) out ivec2 outIndex;
layout(depth_any) out; // float gl_FragDepth

layout(push_constant) uniform ViewIndex {
	int inViewIndex;
};

#include "view.glsl"
#include "light.glsl"


const float NAN = 0.0/0.0;

float calcZEndcap(vec2 xy, uint i, vec3 normals[2]) {

	// intersect the ray with the plane
	float z = dot(inPosCamera[i].xy - xy, normals[i].xy)/normals[i].z + inPosCamera[i].z;

	// do a range check
	bool inRange = distance(vec3(xy, z), inPosCamera[i]) <= inRadiusCamera[i];
	if (inRange) {
		return z;
	}

	return NAN;
}

// p, n are the cylinder point and normalized direction
// r is the cylinder radius
float[2] intersectCylinderRay(vec3 p, vec3 n, float r, float len, vec2 ray) {

	// see math/cylinder.wxmx for the derivation
	float a1 = n.x*n.x;
	float a2 = n.y*n.y;
	float a3 = n.z*n.z;
	float a4 = a3*a3;
	float a5 = a4+(a2+a1-2)*a3+1;
	float a6 = n.x*a1;
	float a7 = n.z*a3;
	float a8 = -a1;
	float a9 = a8+2;
	float a10 = n.y*a2;
	float a11 = -a2;
	float a12 = -a4;
	float a13 = 2*a1;
	float a14 = a1*a1;
	float a15 = a12+(a11-2*a1+2)*a3-a1*a2-a14+a13-1;
	float a16 = -2*n.x*n.y*a3-2*n.x*a10+(4*n.x-2*a6)*n.y;
	float a17 = a2*a2;
	float a18 = a12+(-2*a2+a8+2)*a3-a17+a9*a2-1;
	float a19 = 2*a4;
	float a20 = 2*n.x*n.y*a3+2*n.x*a10+(2*a6-4*n.x)*n.y;
	float z = -(sqrt(a18*ray.y*ray.y+(a16*ray.x+(a19+(4*a2+a13-4)*a3+2*a17+(a13-4)*a2+2)*p.y+a20*p.x)*ray.y+a15*ray.x*ray.x+(a20*p.y+(a19+(2*a2+4*a1-4)*a3+2*a1*a2+2*a14-4*a1+2)*p.x)*ray.x+a5*r*r+a18*p.y*p.y+a16*p.x*p.y+a15*p.x*p.x)+(n.y*a7+(a10+(a1-2)*n.y)*n.z)*ray.y+(n.x*a7+(n.x*a2+a6-2*n.x)*n.z)*ray.x+(a12+(a11+a8+2)*a3-1)*p.z+((a9*n.y-a10)*n.z-n.y*a7)*p.y+((-n.x*a2-a6+2*n.x)*n.z-n.x*a7)*p.x)/(a5);

	// get the normalized distance along the cylindrical axis
	float t = dot(vec3(ray, z) - p, n)/len;

	// if we're out of range, drop the intersection entirely
	if (t < 0 || t > 1) {
		z = NAN;
		t = NAN;
	}

	float result[2] = { z, t };
	return result;
}

void main() {

	// transform from framebuf to camera space
	vec3 posPixelCamera = framebufToCamera(gl_FragCoord);

	// convert to a pos,normal cylinder representation
	vec3 posCylinder = inPosCamera[0];
	vec3 axisCylinder = inPosCamera[1] - inPosCamera[0];
	float len = length(axisCylinder);
	axisCylinder /= len;

	// intersect with the cylindrical surface
	float intersection[2] = intersectCylinderRay(posCylinder, axisCylinder, inRadiusCamera[0], len, posPixelCamera.xy);
	float zCylinder = intersection[0];
	float tCylinder = intersection[1];

	// if we have a valid point on the cylinder, use that first
	if (!isnan(zCylinder)) {

		posPixelCamera.z = zCylinder;

		// compute the normal
		vec3 center = posCylinder + tCylinder*len*axisCylinder;
		vec3 normal = normalize(posPixelCamera - center);

		// pick which end to use based on the cylinder parameter
		uint iEnd = tCylinder <= 0.5 ? 0 : 1;

		outColor = light(inColor[iEnd], normal);
		outIndex = ivec2(inIndex[iEnd], inViewIndex);
		gl_FragDepth = cameraZToClip(posPixelCamera.z);

	} else {

		// otherwise, find out which endcap (if any) intersects
		vec3 normalEndcap[2] = {
			-axisCylinder,
			axisCylinder
		};
		float zEndcap[2] = {
			calcZEndcap(posPixelCamera.xy, 0, normalEndcap),
			calcZEndcap(posPixelCamera.xy, 1, normalEndcap)
		};

		// did we hit any endcaps?
		int iEndcap = -1;
		if (!isnan(zEndcap[0]) && !isnan(zEndcap[1])) {

			// got both, pick the closer one
			if (zEndcap[0] < zEndcap[1]) {
				iEndcap = 0;
			} else {
				iEndcap = 1;
			}

		} else if (!isnan(zEndcap[0])) {
			iEndcap = 0;
		} else if (!isnan(zEndcap[1])) {
			iEndcap = 1;
		}

		if (iEndcap >= 0) {

			// yup, found an endcap
			posPixelCamera.z = zEndcap[iEndcap];

			outColor = light(inColor[iEndcap], normalEndcap[iEndcap]);
			outIndex = ivec2(inIndex[iEndcap], inViewIndex);
			gl_FragDepth = cameraZToClip(posPixelCamera.z);

		} else {

			// no intersection
			outColor = vec4(0);
			outIndex = ivec2(-1, -1);
			gl_FragDepth = 1;
		}
	}
}
