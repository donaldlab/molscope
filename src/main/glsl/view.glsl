
layout(binding = 1, std140) uniform restrict readonly ViewBuf {
	float angle; // TODO: get rid of this
	float zNearCamera;
	float zFarCamera;
	float magnification;
	vec2 windowSize;
	// TODO: camera pos, orientation?
};
