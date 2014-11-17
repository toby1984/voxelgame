uniform sampler2D color_texture;
    
smooth in vec2 vTexCoord;

void main()                                   
{
   // read color
   vec4 v_color = texture2D(color_texture, vTexCoord );
             
   // per-pixel "fog" (actually modifies alpha-channel only)
   const float zFar = 5000;
   const float fogDensity = 1.5; 
   
   float distToCamera = (gl_FragCoord.z / gl_FragCoord.w)/zFar;
   float fogFactor = exp( -pow( fogDensity * distToCamera , 6.0) );   
   fogFactor = clamp(fogFactor, 0.0, 1.0);

   gl_FragColor.rgb=v_color.rgb;
   gl_FragColor.a = v_color.a*fogFactor;   
  
}