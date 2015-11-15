package de.codesourcery.terrain;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL11;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.glutils.IndexBufferObject;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.VertexBufferObject;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import de.codesourcery.voxelgame.core.FPSCameraController;
import de.codesourcery.voxelgame.core.render.FloatArrayBuilder;
import de.codesourcery.voxelgame.core.render.ShortArrayBuilder;

public class TerrainMesh 
{
    public static final boolean DEBUG_PERFORMANCE = true;
    public static final boolean CULL_FACES = true;
    public static final boolean DEPTH_BUFFER = true;
    public static final boolean DEBUG_RENDER_WIREFRAME = false;
    
    public static final int COMPONENTS_PER_VERTEX = 9; // (x,y,z) + (nx,ny,nz) + (r,g,b)
    
    public static final Vector3 LAND_COLOR = new Vector3(0.5f,0.5f,0);
    public static final Vector3 WATER_COLOR = new Vector3(0,0,1);
    
    public static final float TILE_SIZE = 10; // size of a tile in object coordinates
    
    public static final float MAX_TERRAIN_HEIGHT = 50;
    
    public float[] data;
    public int sizeInVertices;
    public int vertexCount;
    
    private final ShortArrayBuilder indexBuilder=new ShortArrayBuilder( 100 ,  100 );
    private final FloatArrayBuilder vertexBuilder=new FloatArrayBuilder( 100 , 100 );
    
    private VertexBufferObject vbo;
    private IndexBufferObject ibo;    
    
    public final ShaderProgram shader;
    
    public final Matrix4 modelMatrix = new Matrix4();
    
    private boolean uploadDataToGPU = true;
    
    public TileChunk chunk;
    
    public TerrainMesh(ShaderManager manager) 
    {
        this.shader = manager.getShader("flatsolid");
    }
    
    public boolean isChunkSet() {
        return chunk != null;
    }
    
    public void set(TileChunk chunk) {
        
        this.sizeInVertices = chunk.sizeInTiles+1;
        if ( this.sizeInVertices < 2 ) {
            throw new IllegalArgumentException("Size in vertices needs to be >= 2");
        }
        
        this.chunk = chunk;
        uploadDataToGPU = true;
        
        this.vertexBuilder.begin();
        this.indexBuilder.begin();
        
        
        final float[] heightData = chunk.heightMap;
        
        final float tileSize = chunk.sizeInTiles * TILE_SIZE;
        final float offsetX = (chunk.location.x*tileSize);
        final float offsetZ = (chunk.location.z*tileSize);
        modelMatrix.setToTranslation( offsetX , 0 , offsetZ );
        System.out.println("===> Chunk "+chunk.location+" translation: ("+offsetX+" , "+offsetZ+") ("+chunk.sizeInTiles+" tiles per chunk, "+TILE_SIZE+" each)");
        
        this.vertexCount = sizeInVertices*sizeInVertices;
        
        this.data = new float[ vertexCount * COMPONENTS_PER_VERTEX ];
        
        // points of current quad
        final Vector3 p0 = new Vector3();
        final Vector3 p1 = new Vector3();
        final Vector3 p2 = new Vector3();
        final Vector3 p3 = new Vector3();
        
        final int sizeInTiles = sizeInVertices-1;
        
        Vector3 color0,color1,color2;
        
        float z = -(sizeInTiles/2f) * TILE_SIZE;
        int vertexIndex = 0;
        for ( int tileZ = 0 ; tileZ < sizeInTiles ; tileZ++ , z += TILE_SIZE ) 
        {
            float x = -(sizeInTiles/2f) * TILE_SIZE;
            for ( int tileX = 0 ; tileX < sizeInTiles ; tileX++ , x += TILE_SIZE ) 
            {
                final Tile tile = chunk.getTile(tileX, tileZ);
                
                final float h3 = heightData[ tileZ*sizeInVertices + tileX     ];
                final float h2 = heightData[ tileZ*sizeInVertices + tileX + 1 ];
                final float h1 = heightData[ (tileZ+1)*sizeInVertices + tileX + 1 ];
                final float h0 = heightData[ (tileZ+1)*sizeInVertices + tileX ];                
                
                p3.set( x          , MAX_TERRAIN_HEIGHT*h3, z );
                p2.set( x+TILE_SIZE , MAX_TERRAIN_HEIGHT*h2, z );
                p1.set( x+TILE_SIZE , MAX_TERRAIN_HEIGHT*h1, z+TILE_SIZE );
                p0.set( x          , MAX_TERRAIN_HEIGHT*h0, z+TILE_SIZE );
                
                color0 = tile.isWater() ? WATER_COLOR : LAND_COLOR;
                color1 = tile.isWater() ? WATER_COLOR : LAND_COLOR;
                color2 = tile.isWater() ? WATER_COLOR : LAND_COLOR;
                
                // triangle #1
                vertexIndex=addTriangle( vertexIndex , p0 , p2 , p3 , color0,color1,color2 );
                
                // triangle #2
                vertexIndex=addTriangle( vertexIndex , p0 , p1 , p2 , color0,color1,color2 );
            }
        }
        
        vertexBuilder.end();
        indexBuilder.end();
    }
    
    private int addTriangle(int vertexIndex,Vector3 p0,Vector3 p1,Vector3 p2,Vector3 color0,Vector3 color1,Vector3 color2) 
    {
        final Vector3 tmp1 = new Vector3();
        final Vector3 tmp2 = new Vector3();
        
        tmp1.set( p1 ).sub( p2 );
        tmp2.set( p0 ).sub( p2 );
        tmp2.crs( tmp1 ).nor();
        
        // p2
        vertexBuilder.put( p2.x ,  p2.y , p2.z , tmp2.x , tmp2.y , tmp2.z , color2.x ,color2.y , color2.z );
        
        // p1
        vertexBuilder.put( p1.x ,  p1.y , p1.z , tmp2.x , tmp2.y , tmp2.z , color1.x ,color1.y , color1.z);
        
        // p0
        vertexBuilder.put( p0.x ,  p0.y , p0.z , tmp2.x , tmp2.y , tmp2.z , color0.x ,color0.y , color0.z);
        
        int pi2 = vertexIndex;
        int pi1 = vertexIndex+1;
        int pi0 = vertexIndex+2;   
        
        indexBuilder.put( (short) pi0 , (short) pi1 , (short)pi2 );        
        return vertexIndex+3;
    }
    
    public void render(FPSCameraController camera) {

        final int vertexCount = vertexBuilder.actualSize() / COMPONENTS_PER_VERTEX;
        if ( vertexCount == 0 ) {
            return;
        }
        
        if ( vbo == null ||  vbo.getNumMaxVertices() < vertexCount  )
        {
            if ( vbo != null ) {
                vbo.dispose();
                vbo = null;
            }
            if ( DEBUG_PERFORMANCE ) {
                System.out.println("Creating VBO for "+vertexCount+" vertices.");
            }
            vbo = createVBO( vertexCount );
            uploadDataToGPU = true;
        }

        final int indexCount = indexBuilder.actualSize();
        if ( ibo == null ||  ibo.getNumMaxIndices() < indexCount  )
        {
            if ( ibo != null ) {
                ibo.dispose();
                ibo = null;
            }
            if ( DEBUG_PERFORMANCE ) {
                System.out.println("Creating VBO for "+indexCount+" indices.");
            }
            ibo = createIBO( indexCount );
            uploadDataToGPU = true;
        }

        if ( CULL_FACES ) {
            Gdx.graphics.getGL20().glEnable( GL20.GL_CULL_FACE );
        }
        if ( DEPTH_BUFFER ) {
            Gdx.graphics.getGL20().glEnable(GL11.GL_DEPTH_TEST);
        }
        
        shader.begin();
        
        setupPhongShader( camera );
        
        vbo.bind( shader );
        ibo.bind();

        if ( uploadDataToGPU ) {
            vbo.setVertices( vertexBuilder.array , 0 , vertexBuilder.actualSize() );
            ibo.setIndices( indexBuilder.array , 0 , indexBuilder.actualSize() );
            uploadDataToGPU = false;
        }

        int indicesRemaining = indexCount;
        while ( indicesRemaining > 0 )
        {
            final int indicesToDraw = indicesRemaining > 65535 ? 65535 : indicesRemaining;
            if ( DEBUG_RENDER_WIREFRAME )
            {
                Gdx.graphics.getGL20().glDrawElements(GL20.GL_LINES , indicesToDraw , GL20.GL_UNSIGNED_SHORT , 0);
            } else {
                Gdx.graphics.getGL20().glDrawElements(GL20.GL_TRIANGLES, indicesToDraw , GL20.GL_UNSIGNED_SHORT , 0);
            }
            indicesRemaining -= indicesToDraw;
        }

        if ( DEPTH_BUFFER ) {
            Gdx.graphics.getGL20().glDisable(GL11.GL_DEPTH_TEST);
        }

        if ( CULL_FACES ) {
            Gdx.graphics.getGL20().glDisable( GL20.GL_CULL_FACE );
        }

        ibo.unbind();
        vbo.unbind(shader);
        
        shader.end();
    }
    
    private void setupPhongShader(FPSCameraController camera) 
    {
        final Matrix4 mv = new Matrix4(camera.camera.view ).mul( modelMatrix );
        final Matrix4 mvp = new Matrix4(camera.camera.combined).mul( modelMatrix );
        
        shader.setUniformMatrix( "u_modelView" , mv );   
        shader.setUniformMatrix( "u_modelViewProjection" , mvp );
        shader.setUniformMatrix("u_cameraRotation" , camera.normalMatrix );
    }
    
    public void dispose() 
    {
        if ( vbo != null ) 
        {
            vbo.dispose();
        }
        
        if ( ibo != null ) {
            ibo.dispose();
        }
    }
    
    private IndexBufferObject createIBO(int indexCount) {
        return new IndexBufferObject(true,indexCount);
    }
    
    private VertexBufferObject createVBO(int vertexCount)
    {
//        final VertexAttribute lightFactorAttribute  = new VertexAttribute( 250 , 1 , "a_lightFactor" ); // value in the range 0...1
        final VertexAttribute[] attrs = new VertexAttribute[]
        {
            new VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE),
            new VertexAttribute(250, 3, "a_color" )
            // lightFactorAttribute // a float in the range [0...1] (no light ... full light)
        };
        return new VertexBufferObject(true, vertexCount , attrs );
    }
}
