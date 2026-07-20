#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    bool dark = vertexColor.r < 0.15 && vertexColor.g < 0.15 && vertexColor.b < 0.15;
    bool translucent = vertexColor.a > 0.05 && vertexColor.a < 0.95;
    if (dark && translucent) {
        vertexColor.a = 0.0;
    }
}
