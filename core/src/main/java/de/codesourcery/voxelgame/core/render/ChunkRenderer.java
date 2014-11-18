package de.codesourcery.voxelgame.core.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;

import de.codesourcery.voxelgame.core.Block;
import de.codesourcery.voxelgame.core.FPSCameraController;
import de.codesourcery.voxelgame.core.Main;
import de.codesourcery.voxelgame.core.world.Chunk;
import de.codesourcery.voxelgame.core.world.ChunkManager;
import de.codesourcery.voxelgame.core.world.IChunkVisitor;

public class ChunkRenderer implements Disposable , IChunkRenderer {

	private static final boolean DEBUG_PERFORMANCE = false;

	private long frame = 0;

	private final ShaderProgram regularShader;
	private final ShaderProgram wireframeShader;

	private final ChunkManager chunkManager;
	private final FPSCameraController cameraController;

	private final ShapeRenderer shapeRenderer;

	public ChunkRenderer(ChunkManager chunkManager,FPSCameraController cameraController)
	{
		this.chunkManager = chunkManager;
		this.cameraController = cameraController;
		this.regularShader = loadRegularShader();
		this.wireframeShader = loadWireframeShader();
		this.shapeRenderer = new ShapeRenderer();
	}

	private static ShaderProgram loadRegularShader()
	{
		return loadShader("/flat_vertex.glsl","/flat_fragment.glsl");
	}

	private static ShaderProgram loadWireframeShader()
	{
		return loadShader("/wireframe_vertex.glsl","/wireframe_fragment.glsl");
	}

	public static ShaderProgram loadShader(String vertexClassPath,String fragmentClassPath)
	{
		try {
			final String vertex = readShaderFromClasspath( vertexClassPath );
			final String fragment = readShaderFromClasspath( fragmentClassPath );
			final ShaderProgram result = new ShaderProgram(vertex,fragment);
			if ( ! result.isCompiled() ) {

				throw new RuntimeException("Failed to compile shaders: "+result.getLog());
			}
			return result;
		}
		catch (final IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private static String readShaderFromClasspath(String name) throws IOException {

		System.out.println("LOADING SHADER: "+name);
		final InputStream stream = Main.class.getResourceAsStream( name );
		if ( stream == null ) {
			throw new RuntimeException("Failed to load shader '"+name+"'");
		}
		final StringBuilder result = new StringBuilder();
		final BufferedReader reader = new BufferedReader(new InputStreamReader(stream ) );
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

	@Override
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

	@Override
	public boolean isWireframe() {
		return BlockRenderer.DEBUG_RENDER_WIREFRAME;
	}

	@Override
	public void setWireframe(boolean showWireframe) {
		BlockRenderer.DEBUG_RENDER_WIREFRAME = showWireframe;
	}

	private void renderChunk(Chunk chunk)
	{
		final GL20 gl20 = Gdx.graphics.getGL20();

		gl20.glEnable (GL20.GL_BLEND);
	    gl20.glBlendFunc (GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

	    final ShaderProgram shader = BlockRenderer.DEBUG_RENDER_WIREFRAME ? wireframeShader : regularShader;

		shader.begin();

//		shader.setUniformf("u_cameraPosition" , cameraController.camera.position );
		shader.setUniformMatrix("u_modelViewProjection", cameraController.camera.combined );
		if ( ! BlockRenderer.DEBUG_RENDER_WIREFRAME ) {
			shader.setUniformMatrix("u_modelView", cameraController.camera.view );
			shader.setUniformMatrix("u_cameraRotation" , cameraController.normalMatrix );
		}

		chunk.blockRenderer.render( shader );

		shader.end();

	    if ( BlockRenderer.DEBUG_RENDER_WIREFRAME)
	    {
			final float xOrig = chunk.boundingBox.min.x;
			final float yOrig = chunk.boundingBox.min.y;
			final float zOrig = chunk.boundingBox.max.z;

			shapeRenderer.setTransformMatrix(new Matrix4().idt() );
			shapeRenderer.setProjectionMatrix( cameraController.camera.combined );
			shapeRenderer.begin(ShapeType.Line);
			shapeRenderer.setColor(Color.RED);
			shapeRenderer.box( xOrig,yOrig,zOrig,
					Chunk.BLOCK_WIDTH*Chunk.BLOCKS_X,
					Chunk.BLOCK_HEIGHT*Chunk.BLOCKS_Y,
					Chunk.BLOCK_DEPTH*Chunk.BLOCKS_Z);
			shapeRenderer.end();
	    }

	    gl20.glBlendFunc (GL20.GL_ONE, GL20.GL_ZERO);
	    gl20.glDisable(GL20.GL_BLEND);
	}

	@Override
	public void dispose()
	{
		wireframeShader.dispose();
		regularShader.dispose();
		shapeRenderer.dispose();
	}

	/**
	 * Initializes the mesh for a given chunk
	 * @param chunk
	 * @return
	 */
	@Override
	public int setupMesh(Chunk chunk) {

		int quadCount = 0; // debug
		int renderedBlocks = 0; // debug

		final float xOrig = chunk.boundingBox.min.x;
		final float yOrig = chunk.boundingBox.min.y;
		final float zOrig = chunk.boundingBox.min.z;

		final BlockRenderer renderer = chunk.blockRenderer;
		renderer.begin();

		if ( ! chunk.isEmpty() )
		{
			final byte[] blocks = chunk.blockType;

			for ( int x = 0 ; x < Chunk.BLOCKS_X ; x++ )
			{
				final float blockCenterX = xOrig + x * Chunk.BLOCK_WIDTH+(Chunk.BLOCK_WIDTH*0.5f);

				for ( int y = 0 ; y < Chunk.BLOCKS_Y ; y++ )
				{
					final float blockCenterY = yOrig + y * Chunk.BLOCK_HEIGHT+(Chunk.BLOCK_HEIGHT*0.5f);
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
								final float blockCenterZ = zOrig + z * Chunk.BLOCK_DEPTH+(Chunk.BLOCK_DEPTH*0.5f);

								if ( DEBUG_PERFORMANCE ) {
									quadCount += Integer.bitCount( sidesMask );
								}
								final float lightLevel = chunk.lightLevel[ currentIndex ];
								final float lightFactor = 0.3f + lightLevel*(0.7f/(Block.MAX_LIGHT_LEVEL+1));
								// final float lightFactor = 0.7f;
								renderer.addBlock( blockCenterX , blockCenterY , blockCenterZ , Chunk.BLOCK_DEPTH/2.0f ,lightFactor, blockType , sidesMask );
								if ( DEBUG_PERFORMANCE ) {
									renderedBlocks++;
								}
							}
						}
					}
				}
			}
		}

		renderer.end();

		chunk.setMeshRebuildRequired( false );
		chunk.renderedBlockCount = renderedBlocks;

		if ( DEBUG_PERFORMANCE && (frame%60)==0)
		{
			System.out.println("Triangle count: "+quadCount*2);
		}
		return renderedBlocks;
	}

	private int determineSidesToRender(Chunk chunk,int blockX,int blockY,int blockZ)
	{
		final byte[] blocks = chunk.blockType;

		int sideMask = 0;

		// check along X axis
		if ( blockX == 0 )
		{
			// check adjacent chunk left of this one
			final Chunk adj = chunkManager.maybeGetChunk( chunk.x-1 ,  chunk.y ,  chunk.z );
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
			final Chunk adj = chunkManager.maybeGetChunk( chunk.x+1 ,  chunk.y ,  chunk.z );
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
			final Chunk adj = chunkManager.maybeGetChunk( chunk.x ,  chunk.y+1 ,  chunk.z );
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
			final Chunk adj = chunkManager.maybeGetChunk( chunk.x ,  chunk.y-1 ,  chunk.z );
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
			final Chunk adj = chunkManager.maybeGetChunk( chunk.x ,  chunk.y ,  chunk.z-1 );
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
			final Chunk adj = chunkManager.maybeGetChunk( chunk.x ,  chunk.y ,  chunk.z+1 );
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