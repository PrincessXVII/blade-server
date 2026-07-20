#version 330

// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl and projection.glsl
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;

out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    if(vertexColor.r == 0.0627451 && vertexColor.a >= 0.7 && vertexColor.a < 0.9) {
		  vertexColor.a = 0; // this removes the background. You can set the transparency to whatever you want (0 is fully transparent, 1 is the opposite)
	}
}
