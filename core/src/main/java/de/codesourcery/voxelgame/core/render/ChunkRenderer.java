package de.codesourcery.voxelgame.core.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelgame.core.Block;
import de.codesourcery.voxelgame.core.FPSCameraController;
import de.codesourcery.voxelgame.core.Main;
import de.codesourcery.voxelgame.core.world.Chunk;
import de.codesourcery.voxelgame.core.world.ChunkManager;
import de.codesourcery.voxelgame.core.world.IChunkVisitor;

public class ChunkRenderer implements Disposable , IChunkRenderer {

	private static final boolean DEBUG_PERFORMANCE = true;

	private long frame = 0;

	private final ShaderProgram shader;
	private final ChunkManager chunkManager;
	private final FPSCameraController cameraController;

	public ChunkRenderer(ChunkManager chunkManager,FPSCameraController cameraController) 
	{
		this.chunkManager = chunkManager;
		this.cameraController = cameraController;
		this.shader = loadShader();
	}

	private static ShaderProgram loadShader() {
		return loadShader("/flat_vertex.glsl","/flat_fragment.glsl");
	}
	
	public static ShaderProgram loadShader(String vertexClassPath,String fragmentClassPath) 
	{
		try {
			String vertex = readShaderFromClasspath( vertexClassPath );
			String fragment = readShaderFromClasspath( fragmentClassPath );
			ShaderProgram result = new ShaderProgram(vertex,fragment);
			if ( ! result.isCompiled() ) {
				
				throw new RuntimeException("Failed to compile shaders: "+result.getLog());
			}
			return result;
		} 
		catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}		
	}

	private static String readShaderFromClasspath(String name) throws IOException {

		System.out.println("LOADING SHADER: "+name);
		InputStream stream = Main.class.getResourceAsStream( name );
		if ( stream == null ) {
			throw new RuntimeException("Failed to load shader '"+name+"'");
		}
		StringBuilder result = new StringBuilder();
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream ) );
		String line;
		try {
			while ( (line = reader.readLine() ) != null ) {
				result.append(line).append("\n");
			}
		} finally {
			reader.close();
		}
		return result.toString();
	}		
	
	private final MyChunkVisitor visitor = new MyChunkVisitor();
	
	protected final class MyChunkVisitor implements IChunkVisitor {

		public int chunkCount = 0;
		public int renderedBlockCount = 0;
		
		@Override
		public void visit(Chunk chunk) {
			renderChunk(chunk);
			renderedBlockCount+=chunk.renderedBlockCount;
			chunkCount++;
		}
		
		public void reset() {
			chunkCount = 0;
			renderedBlockCount=0;
		}
	}

	public void render() 
	{
		// DEBUG
		long renderTime=0;
		if ( DEBUG_PERFORMANCE ) {
			renderTime = -System.currentTimeMillis();
		}
		
		visitor.reset();
		chunkManager.visitVisibleChunks( visitor );

		if ( DEBUG_PERFORMANCE ) 
		{
			renderTime+=System.currentTimeMillis();
			frame++;
			if ( (frame%240)==0) 
			{
				System.out.println("RENDERING: rendering: "+renderTime+" ms ("+visitor.chunkCount+" chunks, "+visitor.renderedBlockCount+" blocks)");
			}			
		}
	}

	private void renderChunk(Chunk chunk) 
	{
		final GL20 gl20 = Gdx.graphics.getGL20();
		
		gl20.glEnable (GL20.GL_BLEND);
	    gl20.glBlendFunc (GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
	    
		shader.begin();

//		shader.setUniformf("u_cameraPosition" , cameraController.camera.position );
		shader.setUniformMatrix("u_modelViewProjection", cameraController.camera.combined );
		shader.setUniformMatrix("u_modelView", cameraController.camera.view );
		
		shader.setUniformMatrix("u_cameraRotation" , cameraController.normalMatrix );		
		
		chunk.blockRenderer.render( shader );
		
		shader.end();
		
	    gl20.glBlendFunc (GL20.GL_ONE, GL20.GL_ZERO);
	    gl20.glDisable(GL20.GL_BLEND);
	}

	@Override
	public void dispose() 
	{
		shader.dispose();
	}

	/**
	 * Initializes the mesh for a given chunk
	 * @param chunk
	 * @return
	 */
	public int setupMesh(Chunk chunk) {

		int quadCount = 0; // debug
		int notCulled = 0; // debug

		final float xOrig = chunk.boundingBox.min.x;
		final float yOrig = chunk.boundingBox.min.y;
		final float zOrig = chunk.boundingBox.min.z;

		final BlockRenderer renderer = chunk.blockRenderer;
		renderer.begin();
		
		final byte[] blocks = chunk.blockType;

		for ( int x = 0 ; x < Chunk.BLOCKS_X ; x++ ) 
		{
			float blockCenterX = xOrig + x * Chunk.BLOCK_WIDTH+(Chunk.BLOCK_WIDTH*0.5f);

			for ( int y = 0 ; y < Chunk.BLOCKS_Y ; y++ ) 
			{
				float blockCenterY = yOrig + y * Chunk.BLOCK_HEIGHT+(Chunk.BLOCK_HEIGHT*0.5f);				
				for ( int z = 0 ; z < Chunk.BLOCKS_Z ; z++ ) 
				{
					final int currentIndex = (x) + Chunk.BLOCKS_X * ( y ) + (Chunk.BLOCKS_X*Chunk.BLOCKS_Y) * ( z ); 
					final byte blockType = blocks[ currentIndex ];
					if ( Block.isNoAirBlock( blockType ) ) 
					{
						int sidesMask;
						// TODO: Rendering translucent blocks needs fixing
						// see http://stackoverflow.com/questions/3388294/opengl-question-about-the-usage-of-gldepthmask/3390094#3390094
						// and http://www.opengl.org/wiki/Transparency_Sorting
						// need to use separate VBOs for translucent blocks and render them
						// back-to-front with depth buffer disabled
						if ( x != 0 && blockType == Block.Type.WATER ) 
						{
							if ( y == Chunk.BLOCKS_Y-1 || Block.isAirBlock( blocks[ (x) + Chunk.BLOCKS_X * ( y+1 ) + (Chunk.BLOCKS_X*Chunk.BLOCKS_Y) * ( z ) ] ) ) {
								sidesMask = BlockRenderer.SIDE_TOP;
							} else {
								sidesMask = 0;
							}
						} else {
							sidesMask = determineSidesToRender( chunk , x , y, z );
						}
						if ( sidesMask  != 0 ) 
						{
							float blockCenterZ = zOrig + z * Chunk.BLOCK_DEPTH+(Chunk.BLOCK_DEPTH*0.5f);

							if ( DEBUG_PERFORMANCE ) {
								quadCount += Integer.bitCount( sidesMask );
							} 
							final float lightLevel = chunk.lightLevel[ currentIndex ];
							final float lightFactor = 0.3f + lightLevel*(0.7f/(float)(Block.MAX_LIGHT_LEVEL+1));
							// final float lightFactor = 0.7f;
							renderer.addBlock( blockCenterX , blockCenterY , blockCenterZ , Chunk.BLOCK_DEPTH/2.0f ,lightFactor, blockType , sidesMask );
							if ( DEBUG_PERFORMANCE ) {
								notCulled++;
							}
						} 
					}
				}				
			}			
		}
		
		renderer.end();
		
		chunk.setMeshRebuildRequired( false );		
		chunk.renderedBlockCount = notCulled;
		
		if ( DEBUG_PERFORMANCE && (frame%60)==0) 
		{
			System.out.println("Triangle count: "+quadCount*2);
		}
		return notCulled;
	}

	private int determineSidesToRender(Chunk chunk,int blockX,int blockY,int blockZ) 
	{
		final byte[] blocks = chunk.blockType;

		int sideMask = 0;

		// check along X axis
		if ( blockX == 0 ) 
		{ 
			// check adjacent chunk left of this one
			Chunk adj = chunkManager.maybeGetChunk( chunk.x-1 ,  chunk.y ,  chunk.z );
			if ( adj == null || adj.isEmpty() || Block.isTranslucentBlock( adj.blockType[ (Chunk.BLOCKS_X-1) + Chunk.BLOCKS_X * ( blockY ) + (Chunk.BLOCKS_X*Chunk.BLOCKS_Y) * ( blockZ ) ] ) ) 
			{
				sideMask = BlockRenderer.SIDE_LEFT;
			}
		} 
		else if ( Block.isTranslucentBlock( blocks[ (blockX-1) + Chunk.BLOCKS_X * ( blockY ) + (Chunk.BLOCKS_X*Chunk.BLOCKS_Y) * ( blockZ ) ] ) ) 
		{
			sideMask = BlockRenderer.SIDE_LEFT;			
		}

		if ( blockX == Chunk.BLOCKS_X-1 ) { // check adjacent chunk right of this one
			Chunk adj = chunkManager.maybeGetChunk( chunk.x+1 ,  chunk.y ,  chunk.z );
			if ( adj == null || adj.isEmpty() || Block.isTranslucentBlock( adj.blockType[ (0) + Chunk.BLOCKS_X * ( blockY ) + (Chunk.BLOCKS_X*Chunk.BLOCKS_Y) * ( blockZ ) ] ) ) 
			{
				sideMask |= BlockRenderer.SIDE_RIGHT;
			}
		} 
		else if ( Block.isTranslucentBlock( blocks[ (blockX+1) + Chunk.BLOCKS_X * ( blockY ) + (Chunk.BLOCKS_X*Chunk.BLOCKS_Y) * ( blockZ ) ] ) ) 
		{
			sideMask |= BlockRenderer.SIDE_RIGHT;
		}		

		// check along Y axis
		if ( blockY == 0 ) { 
			Chunk adj = chunkManager.maybeGetChunk( chunk.x ,  chunk.y+1 ,  chunk.z );
			if ( adj == null || adj.isEmpty() || Block.isTranslucentBlock( adj.blockType[ (blockX) + Chunk.BLOCKS_X * ( 0 ) + (Chunk.BLOCKS_X*Chunk.BLOCKS_Y) * ( blockZ ) ] ) ) 
			{
				sideMask |= BlockRenderer.SIDE_BOTTOM;
			}			
		} 
		else if ( Block.isTranslucentBlock( blocks[ (blockX) + Chunk.BLOCKS_X * ( blockY-1 ) + (Chunk.BLOCKS_X*Chunk.BLOCKS_Y) * ( blockZ ) ] ) ) 
		{
			sideMask |= BlockRenderer.SIDE_BOTTOM;
		}	

		if ( blockY == Chunk.BLOCKS_Y-1 ) 
		{
			// check adjacent chunk
			Chunk adj = chunkManager.maybeGetChunk( chunk.x ,  chunk.y-1 ,  chunk.z );
			if ( adj == null || adj.isEmpty() || Block.isTranslucentBlock( adj.blockType[ (blockX) + Chunk.BLOCKS_X * ( Chunk.BLOCKS_Y-1 ) + (Chunk.BLOCKS_X*Chunk.BLOCKS_Y) * ( blockZ ) ] ) ) 
			{
				sideMask |= BlockRenderer.SIDE_TOP;
			}				
		} 
		else if ( Block.isTranslucentBlock( blocks[ (blockX) + Chunk.BLOCKS_X * ( blockY+1 ) + (Chunk.BLOCKS_X*Chunk.BLOCKS_Y) * ( blockZ ) ] ) ) 
		{
			sideMask |= BlockRenderer.SIDE_TOP;
		}		

		// check along Z axis
		if ( blockZ == 0 ) { // check adjacent chunk
			Chunk adj = chunkManager.maybeGetChunk( chunk.x ,  chunk.y ,  chunk.z-1 );	
			if ( adj == null || adj.isEmpty() || Block.isTranslucentBlock( adj.blockType[ (blockX) + Chunk.BLOCKS_X * ( blockY ) + (Chunk.BLOCKS_X*Chunk.BLOCKS_Y) * ( Chunk.BLOCKS_Z-1 ) ] ) )  
			{
				sideMask |= BlockRenderer.SIDE_BACK;
			}
		} 
		else if ( Block.isTranslucentBlock( blocks[ (blockX) + Chunk.BLOCKS_X * ( blockY ) + (Chunk.BLOCKS_X*Chunk.BLOCKS_Y) * ( blockZ-1 ) ] ) ) 
		{
			sideMask |= BlockRenderer.SIDE_BACK;
		}	

		if ( blockZ == Chunk.BLOCKS_Z-1 ) { // check adjacent chunk
			Chunk adj = chunkManager.maybeGetChunk( chunk.x ,  chunk.y ,  chunk.z+1 );	
			if ( adj == null || adj.isEmpty() || Block.isTranslucentBlock( adj.blockType[ (blockX) + Chunk.BLOCKS_X * ( blockY ) + (Chunk.BLOCKS_X*Chunk.BLOCKS_Y) * ( 0 ) ] ) ) 
			{
				sideMask |= BlockRenderer.SIDE_FRONT;
			}
		} 
		else if ( Block.isTranslucentBlock( blocks[ (blockX) + Chunk.BLOCKS_X * ( blockY ) + (Chunk.BLOCKS_X*Chunk.BLOCKS_Y) * ( blockZ+1 ) ] ) ) 
		{
			sideMask |= BlockRenderer.SIDE_FRONT;
		}		
		return sideMask;
	}	
}