package de.codesourcery.terrain;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.graphics.glutils.ShaderProgram;

public class ShaderManager 
{
    private static final ShaderManager INSTANCE = new ShaderManager();
    
    private final Map<String,ShaderProgram> cache = new HashMap<>();
 
    private ShaderManager() {
    }

    public static ShaderManager getInstance() {
        return INSTANCE;
    }
    
    public void dispose() 
    {
        for ( ShaderProgram p : cache.values() ) {
            p.dispose();
        }
    }
    
    public ShaderProgram getShader(String name) 
    {
        ShaderProgram result = cache.get(name);
        if ( result == null ) {
            result = loadShader(name);
            cache.put(name, result);
        }
        return result;
    }
    
    private static ShaderProgram loadShader(String name)
    {
        final String fragment;
        final String vertex;
        try {
            fragment = loadShaderSource("/"+name+"_fragment.glsl");
            vertex = loadShaderSource("/"+name+"_vertex.glsl");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final ShaderProgram result = new ShaderProgram( vertex , fragment );
        System.out.println( result.getLog() );
        if ( ! result.isCompiled() ) {
            throw new RuntimeException("Failed to compile shader '"+name+"'");
        }
        return result;
    }
    
    public static String loadShaderSource(String fileName) throws IOException {
        
        System.out.println("Loading shader "+fileName+" ...");
        
        final InputStream stream = TerrainMesh.class.getResourceAsStream(fileName);
        if ( stream == null ) {
              throw new FileNotFoundException( fileName );
        }
        final StringBuilder buffer = new StringBuilder();
        String line = null;
        try ( BufferedReader reader = new BufferedReader( new InputStreamReader( stream ) ) ) 
        { 
            while ( ( line = reader.readLine() ) != null ) {
                buffer.append( line ).append("\n");
            }
            return buffer.toString();
        }
    }      
}
