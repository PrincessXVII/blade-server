#version 330

// Removes the dark fullscreen dimming behind inventories / menus (1.21.11).
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
    // Vanilla dim overlay is a near-black translucent quad.
    // Match several observed shades, not only the exact 16/255 red channel.
    bool dark = vertexColor.r < 0.15 && vertexColor.g < 0.15 && vertexColor.b < 0.15;
    bool translucent = vertexColor.a > 0.05 && vertexColor.a < 0.95;
    if (dark && translucent) {
        vertexColor.a = 0.0;
    }
}
