#version 330
#line 1

uniform sampler2D color_texture;
 	
in float v_lightFactor;

smooth in vec2 vTexCoord;
smooth in vec3 rotatedSurfaceNormal;
smooth in vec3 v_lightDir;

void main()                                   
{
   // phong shading
   vec4 v_color = texture2D(color_texture, vTexCoord );
   v_color.rgb = v_color.rgb * v_lightFactor;

   float dotProduct = dot(normalize(rotatedSurfaceNormal),normalize(v_lightDir));
   vec3 shadedColor = max(0.5, dotProduct) * v_color.rgb;
             
   // per-pixel "fog" (actually modifies alpha-channel only)
   const float zFar = 5000;
   const float fogDensity = 1.5; 
   
   float distToCamera = (gl_FragCoord.z / gl_FragCoord.w)/zFar;
   float fogFactor = exp( -pow( fogDensity * distToCamera , 6.0) );   
   fogFactor = clamp(fogFactor, 0.0, 1.0);

   gl_FragColor.rgb=shadedColor;
   gl_FragColor.a = v_color.a*fogFactor;   
   
   // gl_FragColor.rgb=shadedColor*fogFactor+(vec3(0.7,0.7,0.7)*(1-fogFactor));
   // gl_FragColor.a = v_color.a;   
}