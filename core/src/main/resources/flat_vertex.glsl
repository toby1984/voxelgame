#version 330

// per-vertex attributes
in vec4 a_position;
in vec4 a_normal;
in vec4 a_color;
      
uniform mat4 u_modelView;
uniform mat4 u_modelViewProjection;
uniform mat3 u_cameraRotation;
uniform vec4 u_cameraPosition;

// lighting
uniform vec4 u_lightColor;

// fog



// shader output
smooth out vec4 v_color;
smooth out vec3 rotatedSurfaceNormal;
smooth out vec3 v_lightDir;

void main()                   
{	
   // apply camera rotation to vertex normal
   rotatedSurfaceNormal = u_cameraRotation * a_normal.xyz;
   
   // transform vertex to eye coordinates
   vec4 eyeVertex = u_modelView * a_position;
   vec3 eyeVertexNormalized = eyeVertex.xyz / eyeVertex.w;
   
   // const vec3 lightPos = vec3(eyeVertexNormalized.x,-10000,eyeVertexNormalized.z);
   const vec3 lightPos = u_cameraRotation * vec3(0,10000,0);
      
   // normal vector to light source
   v_lightDir = normalize(lightPos - eyeVertex);
   
   v_color = a_color;
   gl_Position =  u_modelViewProjection * a_position;
}