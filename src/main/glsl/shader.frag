#version 450

layout(location = 0) in vec3 inPosWorld;
layout(location = 1) in vec3 inPosFramebuf;
layout(location = 2) in float inRadiusFramebuf;
layout(location = 3) in vec4 inColor;

layout(location = 0) out vec4 outColor;

layout(binding = 0, r32ui) uniform restrict uimage2D depthBuffer;

// TODO: make uniform buf for global transformations
const vec3 windowSize = vec3(320, 240, 10000);

const uint MaxUint = 4294967295;

uint zToDepth(float z) {
	return uint(z*MaxUint/windowSize.z);
}

float depthToZ(uint d) {
	return float(d)*windowSize.z/MaxUint;
}

void main() {

	vec3 relPosFramebuf = vec3(vec2(gl_FragCoord.xy - inPosFramebuf.xy), 0);

	// is this pixel on our sphere?
	float len = length(relPosFramebuf);
	float d = len - inRadiusFramebuf;
	if (d <= 1) { // reach out one extra pixel, to anti-alias the edges

		// calc the z value of the framebuf pos (+z is into the screen)
		// we should only see the -z side of the sphere
		relPosFramebuf.z = -sqrt(max(inRadiusFramebuf*inRadiusFramebuf - relPosFramebuf.x*relPosFramebuf.x - relPosFramebuf.y*relPosFramebuf.y, 0));

		// depth buffer test
		uint depth = zToDepth(relPosFramebuf.z + inPosFramebuf.z);
		uint oldDepth = imageAtomicMin(depthBuffer, ivec2(gl_FragCoord.xy), depth);
		if (depth < oldDepth) {

			// passed depth test

			// compute the alpha based on the input color and the anti-aliasing
			float alpha = inColor.a*(1 - d);

			// calc the sphere normal
			vec3 normal = normalize(relPosFramebuf);

			// apply very simple lambertian lighting
			const float ambient = 0.5;
			const vec3 toLightVec = normalize(vec3(1, -1, -1));
			float diffuse = dot(normal, toLightVec);
			vec3 color = (ambient + diffuse*0.7)*inColor.rgb;

			outColor = vec4(color, alpha);

		} else {

			// failed depth test
			outColor = vec4(0);
		}

	} else {

		// failed sphere test
		outColor = vec4(0);
	}
}
