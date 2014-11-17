#version 330
#line 1

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
smooth out vec3 rotatedSurfaceNormal;
smooth out vec3 v_lightDir;
smooth out vec2 vTexCoord;

out float v_lightFactor;

void main()                   
{	
   // apply camera rotation to vertex normal
   rotatedSurfaceNormal = u_cameraRotation * a_normal.xyz;
   
   // transform vertex to eye coordinates
   vec4 eyeVertex = u_modelView * a_position;
   vec3 eyeVertexNormalized = eyeVertex.xyz / eyeVertex.w;     
   vec3 lightPos = u_cameraRotation * vec3(0,10000,0);
      
   // normal vector to light source
   v_lightDir = normalize(lightPos - eyeVertex.xyz);
   
   gl_Position =  u_modelViewProjection * a_position;
   vTexCoord = a_texCoord;
   v_lightFactor = a_lightFactor;
}