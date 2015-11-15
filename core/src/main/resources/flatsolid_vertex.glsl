#version 330
#line 1

// per-vertex attributes
in vec4 a_position;
in vec3 a_color;
in vec4 a_normal;
      
// uniforms
uniform mat4 u_modelView;
uniform mat4 u_modelViewProjection;
uniform mat3 u_cameraRotation;

// shader output
smooth out vec3 rotatedSurfaceNormal;
smooth out vec3 v_lightDir;
out vec4 v_color;

void main()                   
{   
   // apply camera rotation to vertex normal
   rotatedSurfaceNormal = u_cameraRotation * a_normal.xyz;
   
   // transform vertex to eye coordinates
   vec4 eyeVertex = u_modelView * a_position;
   vec3 eyeVertexNormalized = eyeVertex.xyz / eyeVertex.w;     
   vec3 lightPos = u_cameraRotation * vec3(0,200,0);
      
   // normal vector to light source
   v_lightDir = normalize(lightPos - eyeVertex.xyz);
   
   gl_Position =  u_modelViewProjection * a_position;
   
   v_color.rgb = a_color;
   v_color.a = 1;
}