package de.codesourcery.terrain;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.GL11;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;

import de.codesourcery.voxelgame.core.FPSCameraController;
import de.codesourcery.voxelgame.core.SkyBox;

public class Main implements ApplicationListener {

    private PerspectiveCamera camera;
    private FPSCameraController camController;
    
    private SpriteBatch spriteBatch;
    private BitmapFont font;
    
    private TileChunkManager chunkManager;
    
    private long frameCounter=0;

    private long fpsSum = 0;
    private int minFPS=Integer.MAX_VALUE;
    private int maxFPS=Integer.MIN_VALUE;
    
    private SkyBox skyBox;
    
    private ShaderManager shaderManager;
    
    @Override
    public void create() 
    {
        shaderManager = ShaderManager.getInstance();
        
        chunkManager = new TileChunkManager( shaderManager );
                
        skyBox = new SkyBox();
        
        spriteBatch = new SpriteBatch();
        
        font = new BitmapFont();
        
        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(10,200,10);
        camera.lookAt(0,0,0);

        camera.far = 5000; 
        camera.near = 0.1f;
        camera.update();
        
        camController = new FPSCameraController(camera,camera.direction){

            @Override
            public boolean canTranslateCamera(Camera cam, Vector3 posDelta) {
                return true;
            }

            @Override
            public void cameraTranslated(Camera camera) {
                // TODO Auto-generated method stub
                
            }

            @Override
            public void cameraRotated(Camera camera) {
                // TODO Auto-generated method stub
                
            }

            @Override
            public void toggleWireframe() {
            }

            @Override
            public void onLeftClick() {
                // TODO Auto-generated method stub
                
            }

            @Override
            public void onRightClick() {
                // TODO Auto-generated method stub
                
            }
        };
        Gdx.input.setInputProcessor(camController);
    }
    
    @Override
    public void resize(int width, int height) 
    {
        camera.viewportHeight=height;
        camera.viewportWidth=width;
        camera.update();
    }
    
    @Override
    public void render() 
    {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);
        
        camController.update();
        
        skyBox.render( camera );
        
        chunkManager.render( camController );
        
        renderUI();
    }
    
    private void renderUI() {

        Gdx.graphics.getGL20().glDisable(GL11.GL_DEPTH_TEST);
        Gdx.graphics.getGL20().glDisable( GL20.GL_CULL_FACE );

        spriteBatch.begin();
        
        final float fontHeight = 1.25f*font.getBounds( "XXX" ).height;

        float y = Gdx.graphics.getHeight() - 15;

        frameCounter++;
        int currentFPS = Gdx.graphics.getFramesPerSecond();
        fpsSum += currentFPS;

        if ( frameCounter > 120 ) { // delay determining min/max for 120 frames to account for JVM warm-up etc.
            minFPS = Math.min(minFPS,currentFPS);
            maxFPS = Math.max(maxFPS,currentFPS);
            final float avgFPS = fpsSum / frameCounter;
            font.draw(spriteBatch, "FPS min/avg/max: "+minFPS+" / "+avgFPS+" / "+maxFPS, 10, y);
            y -= fontHeight;
        }
        y -= fontHeight;
        font.draw(spriteBatch, "Camera pos: "+camera.position, 10, y );
        
        spriteBatch.end();        
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() 
    {
        if ( chunkManager != null ) {
            chunkManager.dispose();
        }
        if ( spriteBatch != null ) {
            spriteBatch.dispose();
        }
        if ( font != null ) {
            font.dispose();
        }
        if ( shaderManager != null ) {
            shaderManager.dispose();
        }        
    }
}