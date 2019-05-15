
const vec3 toLight = normalize(vec3(1, 1, -1));

vec3 lambertian(vec3 albedo, vec3 normal) {

	// apply very simple lambertian lighting
	const float ambient = 0.5;
	float diffuse = clamp(dot(normal, toLight), 0, 1)*0.5;
	return (ambient + diffuse)*albedo;
}

vec4 light(vec4 color, vec3 normal) {
	// apply very simple lambertian lighting
	return vec4(lambertian(color.rgb, normal), color.a);
}

vec4 showNormal(vec3 normal) {
	return vec4((normal.xy + vec2(1))/2, 1 - (normal.z + 1)/2, 1);
}

vec4 showZ(float zClip) {
	return vec4(vec3(1 - zClip), 1);
}
