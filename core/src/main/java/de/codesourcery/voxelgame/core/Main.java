package de.codesourcery.voxelgame.core;

import java.io.File;
import java.io.IOException;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;

import de.codesourcery.voxelgame.core.render.BlockRenderer;
import de.codesourcery.voxelgame.core.render.ChunkRenderer;
import de.codesourcery.voxelgame.core.render.IChunkRenderer;
import de.codesourcery.voxelgame.core.util.TextureAtlasUtil;
import de.codesourcery.voxelgame.core.world.Chunk;
import de.codesourcery.voxelgame.core.world.ChunkFactory;
import de.codesourcery.voxelgame.core.world.DefaultChunkManager;
import de.codesourcery.voxelgame.core.world.DefaultChunkManager.Hit;
import de.codesourcery.voxelgame.core.world.DefaultChunkStorage;

public class Main implements ApplicationListener {

	private static final boolean RESTRICT_CAMERA_TO_AIR_BLOCKS = true;

	public static final File CHUNK_STORAGE = new File("/home/tgierke/tmp/chunks");

	public static final File ASSETS_PATH = new File("/home/tgierke/workspace/voxelgame/assets/");

	// start: debugging stuff
	public static final boolean BENCHMARK_MODE = false;
	private static final int BENCHMARK_DURATION_SECONDS = 30;
	
	private long benchmarkStartTime;
	
	private long frameCounter=0;

	private int previousFPS;
	private long fpsJitterSum=0;
	private long fpsSum = 0;
	private int minFPS=Integer.MAX_VALUE;
	private int maxFPS=Integer.MIN_VALUE;
	
	// end: debugging

	private PerspectiveCamera camera;
	private SpriteBatch spriteBatch;
	private FPSCameraController camController;
	private DefaultChunkManager chunkManager;
	private IChunkRenderer chunkRenderer;
	private Texture crosshair;
	private BitmapFont font;
	private SkyBox skyBox;
	private ShapeRenderer shapeRenderer;

	private Texture blockTextures;

	private final Hit targetedBlock = new Hit();
	private boolean nonAirBlockSelected = false;

	private void setupTextures() {
		crosshair = new Texture(Gdx.files.internal("crosshair.png"));

		// setup texture atlas with block textures
		blockTextures = new Texture(Gdx.files.internal("texture_atlas.png"),false);
		if ( blockTextures.getWidth() != blockTextures.getHeight() ) {
			throw new RuntimeException("Internal error, texture atlas is not rectangular ?");
		}

		blockTextures.setFilter(TextureFilter.Nearest,TextureFilter.Linear);
		blockTextures.setWrap( TextureWrap.Repeat , TextureWrap.Repeat );

		BlockRenderer.setupTextureCoordinates( blockTextures.getWidth() , TextureAtlasUtil.BLOCK_TEXTURE_SIZE , TextureAtlasUtil.SUBTEXTURE_SPACING );
	}

	@Override
	public void create () 
	{
		setupTextures();

		camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		camera.position.set(0f, 200f, 0.0000000001f);
		camera.lookAt(0,200,-100);
		camera.near = 1f;
		camera.far = 1200f;
		camera.update();

		skyBox = new SkyBox();

		font = new BitmapFont();

		final DefaultChunkStorage chunkStorage;
		try {
			chunkStorage = new DefaultChunkStorage( CHUNK_STORAGE ,new ChunkFactory( 0xdeadbeef ) );
		}
		catch (IOException e) 
		{
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		
		chunkManager = new DefaultChunkManager(camera,chunkStorage);

		shapeRenderer = new ShapeRenderer();

		spriteBatch = new SpriteBatch();

		camController = new FPSCameraController(camera,camera.direction)
		{
			private final Vector3 tmp = new Vector3();
			private final BoundingBox bb = new BoundingBox();

			private final float PLAYER_WIDTH = 3;
			private final float PLAYER_HEIGHT = 3;
			private final float PLAYER_DEPTH = 3;

			@Override
			public void onLeftClick() 
			{
				if ( nonAirBlockSelected ) 
				{
					targetedBlock.chunk.setBlockType( targetedBlock.blockX , targetedBlock.blockY , targetedBlock.blockZ , chunkManager , Block.Type.AIR );
				}
			}

			@Override
			public void onRightClick() 
			{
				if ( nonAirBlockSelected ) 
				{
					// determine which side of the block the user clicked
					final BoundingBox bb = new BoundingBox();
					Block.populateBoundingBox( targetedBlock.chunk , targetedBlock.blockX , targetedBlock.blockY , targetedBlock.blockZ , bb );

					Chunk chunk = targetedBlock.chunk;
					int blockX = targetedBlock.blockX;
					int blockY = targetedBlock.blockY;
					int blockZ = targetedBlock.blockZ;

					boolean gotSurface = false;
					if ( equals( bb.min.x , targetedBlock.hitPointOnBlock.x ) ) {
						// left side
						gotSurface = true;
						if ( blockX == 0 ) {
							chunk = chunk.getLeftNeighbour( chunkManager );
							blockX = Chunk.BLOCKS_X-1;
						} else {
							blockX--;
						}
					} else if ( equals( bb.min.y , targetedBlock.hitPointOnBlock.y ) ) {
						// bottom
						gotSurface = true;
						if ( blockY == 0 ) {
							chunk = chunk.getBottomNeighbour( chunkManager );
							blockY = Chunk.BLOCKS_Y-1;
						} else {
							blockY--;
						}						
					} else if ( equals( bb.min.z , targetedBlock.hitPointOnBlock.z ) ) {
						// back
						gotSurface = true;
						if ( blockZ == 0 ) {
							chunk = chunk.getBackNeighbour( chunkManager );
							blockZ = Chunk.BLOCKS_Z-1;
						} else {
							blockZ--;
						}							
					} else if ( equals( bb.max.x , targetedBlock.hitPointOnBlock.x ) ) {
						// right
						gotSurface = true;
						if ( blockX == Chunk.BLOCKS_X-1 ) {
							chunk = chunk.getRightNeighbour( chunkManager );
							blockX = 0;
						} else {
							blockX++;
						}						
					} else if ( equals( bb.max.y , targetedBlock.hitPointOnBlock.y ) ) {
						// top
						gotSurface = true;
						if ( blockY == Chunk.BLOCKS_Y-1 ) {
							chunk = chunk.getTopNeighbour( chunkManager );
							blockY = 0;
						} else {
							blockY++;
						}							
					} else if ( equals( bb.max.z , targetedBlock.hitPointOnBlock.z ) ) {
						// front
						gotSurface = true;
						if ( blockZ == Chunk.BLOCKS_Z-1 ) {
							chunk = chunk.getFrontNeighbour( chunkManager );
							blockZ = 0;
						} else {
							blockZ++;
						}							
					} 

					if ( gotSurface ) 
					{
						// only change block if it doesn't contain the camera position
						Block.populateBoundingBox( chunk , blockX , blockY , blockZ , bb );
						if ( ! bb.contains( camera.position ) ) {
							chunk.setBlockType( blockX , blockY , blockZ , chunkManager , Block.Type.SOLID );
						}
					} else {
						System.err.println("Failed to determine surface for hit point "+targetedBlock.hitPointOnBlock+" and BB "+bb);
					}
				}				
			}

			private final boolean equals(double actual,double expected) 
			{
				final float EPSILON = 0.01f;
				return Math.abs( actual - expected ) <= EPSILON;
			}

			@Override
			public boolean canTranslateCamera(Camera cam, Vector3 posDelta) 
			{
				if ( ! RESTRICT_CAMERA_TO_AIR_BLOCKS ) {
					return true;
				}

				tmp.set( cam.position ).add( posDelta );

				bb.min.set( tmp.x-PLAYER_WIDTH/2.0f , tmp.y - PLAYER_HEIGHT/2.0f , tmp.z - PLAYER_DEPTH/2.0f );
				bb.max.set( tmp.x+PLAYER_WIDTH/2.0f , tmp.y + PLAYER_HEIGHT/2.0f , tmp.z + PLAYER_DEPTH/2.0f );				
				bb.set(bb.min,bb.max);

				if ( chunkManager.intersectsNonEmptyBlock( bb ) )
				{
					System.out.println("REFUSING CAMERA MOVE");
					return false;
				}
				return true;
			}

			@Override
			public void cameraRotated(Camera camera) {
				chunkManager.cameraMoved();				
			}

			@Override
			public void cameraTranslated(Camera camera) {
				chunkManager.cameraMoved();
			}
		}; 

		chunkRenderer = new ChunkRenderer(chunkManager,camController);    
		chunkManager.setChunkRenderer( chunkRenderer ); // TODO: Circular dependency ChunkManager <-> ChunkRenderer ... not that nice...
		chunkManager.cameraMoved();
		Gdx.input.setInputProcessor(camController);	
	}

	@Override
	public void render () 
	{		
		if ( BENCHMARK_MODE ) 
		{ 
			if ( benchmarkStartTime == 0 ) 
			{
				benchmarkStartTime = System.currentTimeMillis();
				Gdx.app.postRunnable( new Runnable() {

					@Override
					public void run() {
						Gdx.input.getInputProcessor().keyDown( Keys.W );
					}
				} );
			} 
			else 
			{
				long now = System.currentTimeMillis();
				final float durationSeconds = ( now - benchmarkStartTime ) / 1000.0f;
				if ( durationSeconds >= BENCHMARK_DURATION_SECONDS ) 
				{
					final float avgFpsJitter = fpsJitterSum / (float) frameCounter; 
					final float avgFps = fpsSum/(float) frameCounter;
					final float avgFps2 = frameCounter / durationSeconds;
					
					System.out.println("Benchmark finished after "+durationSeconds+" seconds ("+frameCounter+" frames");
					System.out.println("Frame count: "+frameCounter);
					System.out.println("min FPS    : "+minFPS);
					System.out.println("avg FPS    : "+avgFps+" ("+avgFps2+")");
					System.out.println("max FPS    : "+maxFPS);
					System.out.println("avg. FPS jitter: "+avgFpsJitter);
					Gdx.app.exit();
				}
			}
		}

		try 
		{
			doRender();
		} catch(RuntimeException e) {
			e.printStackTrace();
			throw e;
		}
	}

	public void doRender () 
	{
		final int currentFPS = Gdx.graphics.getFramesPerSecond();
		
		if ( frameCounter > 0 ) 
		{
			this.fpsJitterSum += Math.abs( previousFPS - currentFPS );
		}
		previousFPS = currentFPS;
		frameCounter++;

		Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		Gdx.gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

		camController.update();

		// render sky
		skyBox.render(camera);

		// bind block texture atlas
		blockTextures.bind();

		// render blocks
		chunkRenderer.render();

		// render outline of targeted block

		// get pick-ray through center of screen
		final Ray r = camera.getPickRay(Gdx.graphics.getWidth()/2.0f,Gdx.graphics.getHeight()/2.0f);
		if ( chunkManager.getClosestIntersection( r , targetedBlock ) ) 
		{
			nonAirBlockSelected = true;
			final Chunk chunk = targetedBlock.chunk;
			final float xOrig = chunk.boundingBox.min.x;
			final float yOrig = chunk.boundingBox.min.y;
			final float zOrig = chunk.boundingBox.min.z;        	

			float bottomLeftX = xOrig + targetedBlock.blockX * Chunk.BLOCK_WIDTH; // +(Chunk.BLOCK_WIDTH*0.5f);
			float bottomLeftY = yOrig + targetedBlock.blockY * Chunk.BLOCK_HEIGHT; // +(Chunk.BLOCK_HEIGHT*0.5f);		
			float bottomLeftZ = zOrig + targetedBlock.blockZ * Chunk.BLOCK_DEPTH +(Chunk.BLOCK_DEPTH*1);

			shapeRenderer.setTransformMatrix(new Matrix4().idt() );
			shapeRenderer.setProjectionMatrix( camera.combined );
			shapeRenderer.begin(ShapeType.Line);
			shapeRenderer.setColor(Color.BLACK);
			shapeRenderer.box( bottomLeftX,bottomLeftY,bottomLeftZ,Chunk.BLOCK_WIDTH,Chunk.BLOCK_HEIGHT,Chunk.BLOCK_DEPTH);
			shapeRenderer.end();
		} else {
			nonAirBlockSelected = false;
		}

		// render UI
		spriteBatch.begin();
		final int centerX = Gdx.graphics.getWidth()/2;
		final int centerY = Gdx.graphics.getHeight()/2;
		spriteBatch.draw(crosshair,centerX-crosshair.getWidth()/2,centerY-crosshair.getHeight()/2);

		// debug output
		final float fontHeight = 1.25f*font.getBounds( "XXX" ).height;

		float y = Gdx.graphics.getHeight() - 15;

		fpsSum += currentFPS;

		if ( frameCounter > 120 ) { // delay determining min/max for 120 frames to account for JVM warm-up etc. 
			minFPS = Math.min(minFPS,currentFPS);
			maxFPS = Math.max(maxFPS,currentFPS);
			float avgFPS = fpsSum / frameCounter;
			font.draw(spriteBatch, "FPS min/avg/max: "+minFPS+" / "+avgFPS+" / "+maxFPS, 10, y);
			y -= fontHeight;        	
		}
		font.draw(spriteBatch, "Chunk : "+chunkManager.cameraChunkX+" / "+chunkManager.cameraChunkY+" / "+chunkManager.cameraChunkZ, 10, y);
		y -= fontHeight;
		font.draw(spriteBatch, "Camera pos: "+camera.position, 10, y );      
		spriteBatch.end();
	}

	@Override
	public void dispose () 
	{
		chunkRenderer.dispose();
		chunkManager.dispose();
		
		crosshair.dispose();
		blockTextures.dispose();
		font.dispose();
		skyBox.dispose();

		spriteBatch.dispose();
		shapeRenderer.dispose();
	}

	@Override
	public void resize(int width, int height) 
	{
		camera.viewportHeight=height;
		camera.viewportWidth=width;
		camera.update();

		spriteBatch.getProjectionMatrix().setToOrtho2D(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		spriteBatch.setProjectionMatrix( spriteBatch.getProjectionMatrix() );
	}

	@Override
	public void pause() {
		// TODO Auto-generated method stub

	}

	@Override
	public void resume() {
		// TODO Auto-generated method stub
	}	
}