
smooth in vec4 v_color;
smooth in vec3 rotatedSurfaceNormal;
smooth in vec3 v_lightDir;

void main()                                   
{
   // phong shading
   vec3 shadedColor = v_color.rgb;
   float dotProduct = dot(normalize(rotatedSurfaceNormal),normalize(v_lightDir));
   shadedColor = max(0.5, dotProduct) * v_color.rgb;
       
   gl_FragColor.rgb=shadedColor.rgb;
   
   // per-pixel "fog" (actually modifies alpha-channel only)
   const float zFar = 1700;
   const float fogDensity = 2; 
   
   float distToCamera = (gl_FragCoord.z / gl_FragCoord.w)/zFar;
   float fogFactor = exp( -pow( fogDensity * distToCamera , 6.0) );   
   fogFactor = clamp(fogFactor, 0.0, 1.0);

   gl_FragColor.a = v_color.a * fogFactor;   
}