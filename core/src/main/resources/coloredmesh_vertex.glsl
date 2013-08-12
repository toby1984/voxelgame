#version 330

uniform mat4 mvp;

in vec4 a_position;
in vec4 a_color;

out vec4 fragColor;

void main(void) {
  gl_Position = mvp * a_position;
  fragColor = a_color;
}