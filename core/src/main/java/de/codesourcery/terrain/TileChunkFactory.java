package de.codesourcery.terrain;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.codesourcery.terrain.Tile.TileType;
import de.codesourcery.terrain.TileChunk.TileChunkKey;
import de.codesourcery.voxelgame.core.util.SimplexNoise;

public class TileChunkFactory 
{
    public static final int SIZE_IN_TILES = 20;
    public static final int SIZE_IN_VERTICES = SIZE_IN_TILES+1;
    
    private static final File BASE_DIR = new File("tmp");
    
    private static final int MAGIC = 0xdeadbeef;
    private static final int FILE_VERSION = 1;
    
    private SimplexNoise noise = new SimplexNoise(0xdeadbeef);
    
    private TileChunk loadChunk(TileChunkKey location) throws FileNotFoundException, IOException 
    {
        final File file = getFileName(location);
        if ( ! file.exists() ) {
            return null;
        }
        try {
            try ( BufferedInputStream in = new BufferedInputStream( new FileInputStream( file ) ) ) {
                return readChunk( in );
            }
        } 
        catch(IOException e) 
        {
            file.delete();
            throw e;
        }
    }
    
    private void writeChunk(TileChunk chunk) throws IOException 
    {
        final File file = getFileName( chunk.location );
        System.out.println("Saving chunk "+chunk+" to "+file.getAbsolutePath());
        
        try ( OutputStream out = new FileOutputStream( file ) )  
        {
            writeInt( MAGIC , out );
            writeInt( FILE_VERSION , out );
            
            writeInt( chunk.location.x, out );
            writeInt( chunk.location.z, out );
            
            writeInt( chunk.sizeInTiles , out );
            
            writeArray( chunk.heightMap , out );
        }
    }
    
    private TileChunk readChunk(InputStream in) throws IOException 
    {
        final int magic = readInt(in);
        if ( magic != MAGIC ) {
            throw new IOException("Bad magic");
        }
        final int version = readInt(in);
        if ( version != FILE_VERSION ) {
            throw new IOException("Bad version (got "+version+" , expected "+FILE_VERSION+")");
        }
        int locX = readInt(in);
        int locZ = readInt(in);
        int sizeInTiles = readInt(in);
        float[] heightMap = readFloatArray(in);
        return new TileChunk( new TileChunkKey(locX,locZ), sizeInTiles, heightMap);
    }
    
    private void writeArray(float[] data,OutputStream out) throws IOException {
        writeInt( data.length , out );
        for ( float f : data ) {
            writeFloat( f , out );
        }
    }
    
    private float[] readFloatArray(InputStream in) throws IOException 
    {
        final int len = readInt(in);
        final float[] result = new float[len];
        for ( int i =0 ; i < len ; i++ ) {
            result[i] = readFloat(in);
        }
        return result;
    }
    
    private void writeInt(int value,OutputStream out) throws IOException 
    {
        out.write( ((value>>24) & 0xff) );
        out.write( ((value>>16) & 0xff) );
        out.write( ((value>>8) & 0xff) );
        out.write( (value & 0xff) );
    }
    
    private int readInt(InputStream in) throws IOException 
    {
        int result = 0;
        result |= readByte(in);

        result = result << 8;
        result |= readByte(in);
        
        result = result << 8;
        result |= readByte(in);
        
        result = result << 8;        
        result |= readByte(in);
        return result;
    }
    
    private int readByte(InputStream in) throws IOException 
    {
        int result = in.read();
        if ( result == -1 ) {
            throw new EOFException("Premature end of file");
        }
        return result;
    }
    
    private void writeFloat(float value,OutputStream out) throws IOException 
    {
        writeInt( Float.floatToIntBits( value ) , out );
    }
    
    private float readFloat(InputStream in) throws IOException 
    {
        return Float.intBitsToFloat( readInt(in) );
    }
    
    private File getFileName(TileChunkKey location) 
    {
        final File homeDir = new File( System.getProperty("user.home") );
        final File cacheDir = new File( homeDir , BASE_DIR.getName() );
        if ( ! cacheDir.exists() ) 
        {
            if ( ! cacheDir.mkdir() ) {
                throw new RuntimeException("Failed to create "+cacheDir.getAbsolutePath() );
            }
            System.out.println("INFO: Storing chunks in "+cacheDir.getAbsolutePath());
        }
        return new File( cacheDir , "chunk_"+location.x+"_"+location.z );
    }
    
    public TileChunk getChunk(TileChunkKey location) 
    {
        TileChunk result=null;
        try {
            result = loadChunk( location );
        }
        catch(IOException e) 
        {
            System.err.println("Failed to load chunk "+location+" from disk: "+e.getMessage());
            e.printStackTrace();
        }
        
        if ( result == null ) {
            System.out.println("Chunk not on disk: "+result);
            result = createNewChunk(location);
            try {
                writeChunk( result );
            } catch (IOException e) {
                System.err.println("Failed to save chunk "+location+" to disk: "+e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Chunk loaded from disk: "+result);
        }
        return result;
    }
    
    private TileChunk createNewChunk(TileChunkKey location) 
    {
        float[] heightMap = createHeightMap( location );
        final TileChunk result = new TileChunk( location , SIZE_IN_TILES,heightMap );
        for (int z = 0 ; z < SIZE_IN_TILES ; z++ ) 
        {
            for (int x = 0 ; x < SIZE_IN_TILES ; x++ ) 
            {
                float h3 = heightMap[ z*SIZE_IN_VERTICES + x ];
                float h2 = heightMap[ z*SIZE_IN_VERTICES + x +1 ];
                float h1 = heightMap[ (z+1)*SIZE_IN_VERTICES + x +1 ];
                float h0 = heightMap[ (z+1)*SIZE_IN_VERTICES + x ];
                result.getTile(x,z).type = (h3==0||h2==0||h1==0||h0==0) ? TileType.WATER : TileType.LAND;
            }
        }
        return result;
    }
    
    // create rectangular heightmap
    private float[] createHeightMap(TileChunkKey loc) 
    {
        final float tileSize = 0.3f;
        float x = loc.x * tileSize;
        float z = loc.z * tileSize;
        
        float[] result = noise.createNoise2D( x ,z , SIZE_IN_VERTICES , tileSize , 3 , 0.5f );
        for ( int i = 0 ; i < result.length ; i++ ) 
        {
            float f = result[i] - 0.3f;
            result[i] = f > 0 ? f : 0;
        }
        
        float min = Float.MAX_VALUE;
        float max = Float.MIN_VALUE;
        for ( float f : result ) {
            min = Math.min( min,f);
            max = Math.max( max,f);
        }
        System.out.println("min: "+min+"/ max: "+max);
        
        float scale = 1f/(max-min);
        for ( int i = 0 ; i < result.length ; i++ ) {
            result[i] = (result[i]-min)*scale;
        }
        return result;
    }    
}
