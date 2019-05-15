
const vec3 toLight = normalize(vec3(1, 1, -1));

vec3 lambertian(vec3 albedo, vec3 normal) {

	// apply very simple lambertian lighting
	const float ambient = 0.5;
	float diffuse = clamp(dot(normal, toLight), 0, 1)*0.5;
	return (ambient + diffuse)*albedo;
}
