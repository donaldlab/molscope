#version 450


// buffer layout is shared with cursorIndex.comp, but declared read/write there
layout(binding = 0, std140) buffer restrict readonly Cursor {
	int isActive;
	int pad0;
	int pad1;
	int pad3;
	ivec2 pos; // offset = 16
	ivec2 indices; // offset = 24
	uvec4 effects; // offset = 32
} inCursor;

layout(binding = 1, rgba8) uniform restrict readonly image2D inColor;
layout(binding = 2, rg32i) uniform restrict readonly iimage2D inIndices;
layout(binding = 3, rgba8ui) uniform restrict readonly uimage2D inEffects;

layout(location = 0) out vec4 outColor;


// these must match the RenderEffect enum
const uint EFFECT_HIGHLIGHT = 1 << 0;
const uint EFFECT_INSET = 1 << 1;
const uint EFFECT_OUTSET = 1 << 2;

struct Effects {
	uint flags;
	vec3 color;
};

const Effects NOP_EFFECTS = Effects(0, vec3(0));


const int DIR_XM = 0;
const int DIR_XP = 1;
const int DIR_YM = 2;
const int DIR_YP = 3;
const int NUM_DIRS = 4;


vec4 applyEffects(vec4 color, Effects effects, Effects neighborEffects[NUM_DIRS], ivec2 indices, ivec2 neighborIndices[NUM_DIRS]) {

	// implement highlights
	if ((effects.flags & EFFECT_HIGHLIGHT) != 0) {
		color.rgb *= vec3(1) + effects.color;
	}

	// implement insets
	if ((effects.flags & EFFECT_INSET) != 0) {
		bool isEdge = false
			|| indices != neighborIndices[DIR_XM]
			|| indices != neighborIndices[DIR_XP]
			|| indices != neighborIndices[DIR_YM]
			|| indices != neighborIndices[DIR_YP];
		if (isEdge) {
			color.rgb = effects.color;
		}
	}

	// implement outsets
	for (uint i=0; i<NUM_DIRS; i++) {
		if ((neighborEffects[i].flags & EFFECT_OUTSET) != 0) {
			if (neighborIndices[i] != indices) {
				color.rgb = neighborEffects[i].color;
			}
		}
	}

	return color;
}

bool inRange(ivec2 p) {
	ivec2 size = imageSize(inIndices);
	return p.x >= 0 && p.x < size.x && p.y >= 0 && p.y < size.y;
}

const ivec2 NULL_INDICES = ivec2(-1, -1);

ivec2 loadIndices(ivec2 p) {
	if (inRange(p)) {
		return imageLoad(inIndices, p).rg;
	} else {
		return NULL_INDICES;
	}
}

Effects unpackEffects(uvec4 effects) {
	return Effects(
		effects.a,
		effects.rgb/vec3(255)
	);
}

Effects loadEffects(ivec2 p) {
	if (inRange(p)) {
		return unpackEffects(imageLoad(inEffects, p));
	} else {
		return Effects(
			0,
			vec3(0, 0, 0)
		);
	}
}


void main() {

	ivec2 p = ivec2(gl_FragCoord.xy);

	// get info for this pixel
	vec4 color = imageLoad(inColor, p);
	ivec2 indices = loadIndices(p);

	// get the neighborhood around the pixel too, since some effects use that
	ivec2 neighborIndices[NUM_DIRS];
	neighborIndices[DIR_XM] = loadIndices(p + ivec2(-1,  0));
	neighborIndices[DIR_XP] = loadIndices(p + ivec2(+1,  0));
	neighborIndices[DIR_YM] = loadIndices(p + ivec2( 0, -1));
	neighborIndices[DIR_YP] = loadIndices(p + ivec2( 0, +1));
	Effects neighborEffects[NUM_DIRS] = { NOP_EFFECTS, NOP_EFFECTS, NOP_EFFECTS, NOP_EFFECTS };

	// apply cursor effects if needed
	if (inCursor.isActive == 1 && inCursor.indices == indices && inCursor.indices != NULL_INDICES) {
		color = applyEffects(color, unpackEffects(inCursor.effects), neighborEffects, inCursor.indices, neighborIndices);
	}

	// but only use neighboring effects for per-pixel effects
	neighborEffects[DIR_XM] = loadEffects(p + ivec2(-1,  0));
	neighborEffects[DIR_XP] = loadEffects(p + ivec2(+1,  0));
	neighborEffects[DIR_YM] = loadEffects(p + ivec2( 0, -1));
	neighborEffects[DIR_YP] = loadEffects(p + ivec2( 0, +1));

	// apply per-pixel effects
	color = applyEffects(color, loadEffects(p), neighborEffects, indices, neighborIndices);

	outColor = color;
}
