#version 330

// per-vertex attributes
in vec4 a_position;
in vec4 a_normal;
in vec2 a_texCoord;
in float a_lightFactor;
      
uniform mat4 u_modelView;
uniform mat4 u_modelViewProjection;
uniform mat3 u_cameraRotation;
uniform vec4 u_cameraPosition;

// lighting
uniform vec4 u_lightColor;

// shader output
smooth out vec2 vTexCoord;

void main()                   
{   
   gl_Position =  u_modelViewProjection * a_position;
   vTexCoord = a_texCoord;
}