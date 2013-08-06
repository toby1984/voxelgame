
smooth in vec4 v_color;
smooth in vec3 rotatedSurfaceNormal;
smooth in vec3 v_lightDir;

void main()                                   
{
   // phong shading
   const vec4 ambientColor=vec4(0.3,0.3,0.3,1);
   vec4 diffuseColor=v_color;

   float diff = max(0.0,dot(normalize(rotatedSurfaceNormal),normalize(v_lightDir)));
   vec3 shadedColor = diff * diffuseColor.rgb;
   
   shadedColor += ambientColor;
   
   // vec3 vReflection = normalize(reflect(-normalize(v_lightDir),normalize(rotatedSurfaceNormal)));
   // float spec = max(0.0,dot(normalize(rotatedSurfaceNormal),vReflection));
   // if ( diff != 0 ) {
   // 		float fSpec = pow(spec,128.0);
   // 		shadedColor += vec3(fSpec,fSpec,fSpec);
   // }
   
   gl_FragColor.rgb=shadedColor.rgb;
   
   // per-pixel "fog" (actually modifies alpha-channel only)
   const float zFar = 1700;
   const float fogDensity = 2; 
   
   float distToCamera = (gl_FragCoord.z / gl_FragCoord.w)/zFar;
   float fogFactor = exp( -pow( fogDensity * distToCamera , 6.0) );   
   fogFactor = clamp(fogFactor, 0.0, 1.0);

   // gl_FragColor = v_color;
   gl_FragColor.a = v_color.a * fogFactor;   
}