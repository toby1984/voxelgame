package de.codesourcery.voxelgame.core.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.lights.PointLight;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelgame.core.Block;
import de.codesourcery.voxelgame.core.FPSCameraController;
import de.codesourcery.voxelgame.core.Main;
import de.codesourcery.voxelgame.core.world.Chunk;
import de.codesourcery.voxelgame.core.world.IChunkManager;

public class ChunkRenderer implements Disposable , IChunkRenderer {

	private static final boolean DEBUG_PERFORMANCE = true;

	private long frame = 0;

	private final ShaderProgram shader;
	private final IChunkManager chunkManager;

	public ChunkRenderer(IChunkManager chunkManager) 
	{
		this.chunkManager = chunkManager;
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
		System.out.println("---- SHADER: "+name+" ----\n"+result);
		return result.toString();
	}		

	public void render(FPSCameraController cameraController,PointLight light,List<Chunk> visibleChunks) 
	{
		// DEBUG
		long renderTime = 0;
		long geometrySetupTime = 0;
		int totalBlockCount = 0;
		int maxBlocks = 0;
		long time = 0;

		for ( int chunkIndex = 0 ; chunkIndex < visibleChunks.size() ; chunkIndex++ ) 
		{
			final Chunk chunk = visibleChunks.get(chunkIndex); 
			if ( chunk.isDisposed() ) {
//				System.err.println("Won't render disposed chunk "+t);
				continue;
			}
			
			BlockRenderer renderer = chunk.blockRenderer;
			if ( renderer == null || chunk.isMeshRebuildRequired() ) 
			{
				if ( DEBUG_PERFORMANCE ) {				
					time = -System.currentTimeMillis();
				}
				if ( renderer != null ) {
					renderer.dispose();
					chunk.blockRenderer = null;
				}
				chunk.blockRenderer = renderer = new BlockRenderer();

				renderer.begin();

				final int blocksRenderedInChunk = createGeometry(chunk);
				if ( DEBUG_PERFORMANCE ) {
					maxBlocks = Math.max(blocksRenderedInChunk ,maxBlocks);
					totalBlockCount += blocksRenderedInChunk;
				}
				renderer.end();
				chunk.setMeshRebuildRequired( false );

				if ( DEBUG_PERFORMANCE ) {
					time += System.currentTimeMillis();
					geometrySetupTime += time;
				}					
			}
			// RENDER 
			if ( DEBUG_PERFORMANCE ) {
				time = -System.currentTimeMillis();
			}
			renderChunk(chunk,cameraController,light);

			if ( DEBUG_PERFORMANCE ) {
				time += System.currentTimeMillis();
				renderTime += time;
			}					
		}

		if ( DEBUG_PERFORMANCE ) 
		{
			frame++;
			if ( (frame%240)==0) 
			{
				System.out.println("RENDERING: geometry setup: "+geometrySetupTime+" ms, rendering: "+renderTime+" ms (blocks: "+totalBlockCount+" , max. per chunk: "+maxBlocks+")");
			}			
		}
	}

	private void renderChunk(Chunk chunk,FPSCameraController cameraController,PointLight light) 
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

	private int createGeometry(Chunk chunk) {

		int sideCount=0; // debug
		int notCulled = 0; // debug

		final float xOrig = chunk.bb.min.x;
		final float yOrig = chunk.bb.min.y;
		final float zOrig = chunk.bb.min.z;

		final BlockRenderer renderer = chunk.blockRenderer;
		final Block[][][] blocks = chunk.blocks;

		for ( int x = 0 ; x < Chunk.BLOCKS_X ; x++ ) 
		{
			float blockCenterX = xOrig + x * Chunk.BLOCK_WIDTH+(Chunk.BLOCK_WIDTH*0.5f);

			for ( int y = 0 ; y < Chunk.BLOCKS_Y ; y++ ) 
			{
				float blockCenterY = yOrig + y * Chunk.BLOCK_HEIGHT+(Chunk.BLOCK_HEIGHT*0.5f);				
				for ( int z = 0 ; z < Chunk.BLOCKS_Z ; z++ ) 
				{
					final Block block = blocks[x][y][z];
					if ( ! block.isAirBlock() ) 
					{
						int sidesMask;
						// TODO: Rendering translucent blocks needs fixing
						// see http://stackoverflow.com/questions/3388294/opengl-question-about-the-usage-of-gldepthmask/3390094#3390094
						// and http://www.opengl.org/wiki/Transparency_Sorting
						// need to use separate VBOs for translucent blocks and render them
						// back-to-front with depth buffer disabled
						if ( x != 0 && block.type == Block.Type.WATER ) 
						{
							if ( y == Chunk.BLOCKS_Y-1 || blocks[x][y+1][z].isAirBlock() ) {
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
								sideCount += Integer.bitCount( sidesMask );
							} 
							final float lightFactor =  block.lightLevel*(1f/(float)(Block.MAX_LIGHT_LEVEL+1));
							renderer.addBlock( blockCenterX , blockCenterY , blockCenterZ , Chunk.BLOCK_DEPTH/2.0f ,lightFactor, block , sidesMask );
							if ( DEBUG_PERFORMANCE ) {
								notCulled++;
							}
						} 
					}
				}				
			}			
		}

		if ( DEBUG_PERFORMANCE && (frame%60)==0) 
		{
			System.out.println("Side count: "+sideCount+" (worst-case: "+notCulled*6+")");
		}
		return notCulled;
	}

	private int determineSidesToRender(Chunk chunk,int blockX,int blockY,int blockZ) 
	{
		final Block[][][] blocks = chunk.blocks;

		int sideMask = 0;

		// check along X axis
		if ( blockX == 0 ) 
		{ 
			// check adjacent chunk left of this one
			Chunk adj = chunkManager.getChunk( chunk.x-1 ,  chunk.y ,  chunk.z );
			if ( adj.isEmpty() || adj.blocks[Chunk.BLOCKS_X-1][blockY][blockZ].isTranslucentBlock() ) 
			{
				sideMask = BlockRenderer.SIDE_LEFT;
			}
		} 
		else if ( blocks[blockX-1][blockY][blockZ].isTranslucentBlock() ) 
		{
			sideMask = BlockRenderer.SIDE_LEFT;			
		}

		if ( blockX == Chunk.BLOCKS_X-1 ) { // check adjacent chunk right of this one
			Chunk adj = chunkManager.getChunk( chunk.x+1 ,  chunk.y ,  chunk.z );
			if ( adj.isEmpty() || adj.blocks[0][blockY][blockZ].isTranslucentBlock() ) 
			{
				sideMask |= BlockRenderer.SIDE_RIGHT;
			}
		} 
		else if ( blocks[blockX+1][blockY][blockZ].isTranslucentBlock() ) 
		{
			sideMask |= BlockRenderer.SIDE_RIGHT;
		}		

		// check along Y axis
		if ( blockY == 0 ) { 
			Chunk adj = chunkManager.getChunk( chunk.x ,  chunk.y+1 ,  chunk.z );
			if ( adj.isEmpty() || adj.blocks[blockX][0][blockZ].isTranslucentBlock() ) 
			{
				sideMask |= BlockRenderer.SIDE_BOTTOM;
			}			
		} 
		else if ( blocks[blockX][blockY-1][blockZ].isTranslucentBlock() ) 
		{
			sideMask |= BlockRenderer.SIDE_BOTTOM;
		}	

		if ( blockY == Chunk.BLOCKS_Y-1 ) 
		{
			// check adjacent chunk
			Chunk adj = chunkManager.getChunk( chunk.x ,  chunk.y-1 ,  chunk.z );
			if ( adj.isEmpty() || adj.blocks[blockX][Chunk.BLOCKS_Y-1][blockZ].isTranslucentBlock() ) 
			{
				sideMask |= BlockRenderer.SIDE_TOP;
			}				
		} 
		else if ( blocks[blockX][blockY+1][blockZ].isTranslucentBlock() ) 
		{
			sideMask |= BlockRenderer.SIDE_TOP;
		}		

		// check along Z axis
		if ( blockZ == 0 ) { // check adjacent chunk
			Chunk adj = chunkManager.getChunk( chunk.x ,  chunk.y ,  chunk.z-1 );	
			if ( adj.isEmpty() || adj.blocks[blockX][blockY][Chunk.BLOCKS_Z-1].isTranslucentBlock() )  
			{
				sideMask |= BlockRenderer.SIDE_BACK;
			}
		} 
		else if ( blocks[blockX][blockY][blockZ-1].isTranslucentBlock() ) 
		{
			sideMask |= BlockRenderer.SIDE_BACK;
		}	

		if ( blockZ == Chunk.BLOCKS_Z-1 ) { // check adjacent chunk
			Chunk adj = chunkManager.getChunk( chunk.x ,  chunk.y ,  chunk.z+1 );	
			if ( adj.isEmpty() || adj.blocks[blockX][blockY][0].isTranslucentBlock() ) 
			{
				sideMask |= BlockRenderer.SIDE_FRONT;
			}
		} 
		else if ( blocks[blockX][blockY][blockZ+1].isTranslucentBlock() ) 
		{
			sideMask |= BlockRenderer.SIDE_FRONT;
		}		
		return sideMask;
	}	

	public void viewFrustumChanged() {
	}	
}