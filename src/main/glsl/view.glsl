
layout(binding = 0, std140) uniform restrict readonly ViewBuf {
	vec3 cameraWorld;
	// 4 bytes pad
	vec3 cameraSide;
	// 4 bytes pad
	vec3 cameraUp;
	// 4 bytes pad
	vec3 cameraLook;
	// 4 bytes pad
	vec2 windowSize;
	float zNearCamera;
	float zFarCamera;
	float magnification;
};

