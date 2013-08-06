package de.codesourcery.voxelgame.core.util;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

public class TextureAtlasUtil 
{

	private static final int SELECTION_RADIUS = 10; // 5 pixels
	
	private BufferedImage image;
	private MyPanel panel;
	
	public static void main(String[] args) throws IOException 
	{
		final BufferedImage image = new BufferedImage(128,128,BufferedImage.TYPE_4BYTE_ABGR);
		
		Color[] colors = {Color.BLACK,Color.PINK,Color.RED,Color.BLUE,Color.GREEN,Color.MAGENTA};
		Graphics graphics = image.getGraphics();
		final int spacing = 2;
		final int xOrigin = 4;
		final int yOrigin = 4;
		final int sizeInPixels = 16; 
		for ( int i = 0 ; i <colors.length;i++) {
			int x1 = xOrigin + i*sizeInPixels+(i-1)*spacing;
			int y1 = yOrigin;
			int x2 = x1 + sizeInPixels;
			int y2 = sizeInPixels;
			graphics.setColor( colors[i] );
			graphics.fillRect(x1,y1,(x2-x1),(y2-y1));
		}
		
		new TextureAtlasUtil().run( image );
	}
	
	protected static final class Selection 
	{
		public int x0;
		public int y0;
		public int x1;
		public int y1;
		
		public void renderWithCenter(Graphics g) 
		{
			render(g);
			
			Point center = getCenter();
			
			g.drawLine( center.x , center.y , center.x + (SELECTION_RADIUS/2) , center.y );
			g.drawLine( center.x , center.y , center.x - (SELECTION_RADIUS/2) , center.y );
			
			g.drawLine( center.x , center.y , center.x,center.y + (SELECTION_RADIUS/2) );
			g.drawLine( center.x , center.y , center.x,center.y - (SELECTION_RADIUS/2) );
		}
		
		public void render(Graphics g) 
		{
			g.setXORMode( Color.LIGHT_GRAY );
			
			int xMin = Math.min(x0,x1);
			int yMin = Math.min(y0,y1);
			
			int xMax = Math.max(x0,x1);
			int yMax = Math.max(y0,y1);
			
			g.drawRect( xMin,yMin,xMax-xMin,yMax-yMin );
		}
		
		public void fixCoordinates() 
		{
			int xMin = Math.min(x0,x1);
			int yMin = Math.min(y0,y1);
			
			int xMax = Math.max(x0,x1);
			int yMax = Math.max(y0,y1);
			this.x0 = xMin;
			this.y0 = yMin;
			this.x1 = xMax;
			this.y1 = yMax;
		}
		
		public boolean isCloseToCenter(Point p) 
		{
			final Point center=getCenter();
			int dx = center.x - p.x;
			int dy = center.y - p.y;
			double dist = Math.sqrt(dx*dx+dy*dy);
			return dist <= SELECTION_RADIUS; 
		}
		
		public Point getCenter() 
		{
			int xMin = Math.min(x0,x1);
			int yMin = Math.min(y0,y1);
			
			int xMax = Math.max(x0,x1);
			int yMax = Math.max(y0,y1);
			
			int centerX = ( xMin + xMax ) / 2;
			int centerY = ( yMin + yMax ) / 2;
			return new Point(centerX,centerY);
		}
		
		public void moveCenter(Point p) 
		{
			Point center = getCenter();
			int dx = p.x - center.x;
			int dy = p.y - center.y;
			
			x0 += dx;
			x1 += dx;
			y0 += dy;
			y1 += dy;
		}
		
		public boolean isCloseToCorner(Point p) {
			return getCornerInRange( p ) != null;
		}
		
		public boolean isCloseToEdge(Point p) {
			return getEdgeInRange( p ) != null;
		}
		
		public void moveCorner(Corner corner,Point p) 
		{
			switch(corner) 
			{
				case TOP_LEFT:
					x0 = p.x;
					y0 = p.y;
					break;
				case TOP_RIGHT:
					x1 = p.x;
					y0 = p.y;
					break;					
				case BOTTOM_LEFT:
					x0 = p.x;
					y1 = p.y;
					break;						
				case BOTTOM_RIGHT:
					x1 = p.x;
					y1 = p.y;		
					break;
				default:
					throw new IllegalArgumentException("Invalid corner: "+corner);						
			}
		}		
		
		public void moveEdge(Edge edge,Point position) 
		{
			switch(edge) 
			{
				case TOP:
					y0 = position.y;
					break;
				case LEFT:
					x0 = position.x;
					break;
				case RIGHT:
					x1 = position.x;
					break;
				case BOTTOM:
					y1 = position.y;
					break;
				default:
					throw new IllegalArgumentException("Invalid edge: "+edge);						
			}
		}
		
		public Point getCorner(Corner corner) 
		{
			switch(corner) {
				case BOTTOM_LEFT:
					return new Point(x0,y1);
				case BOTTOM_RIGHT:
					return new Point(x1,y1);
				case TOP_LEFT:
					return new Point(x0,y0);
				case TOP_RIGHT:
					return new Point(x1,y0);
				default:
					throw new IllegalArgumentException("Invalid corner: "+corner);
			}
		}
		
		public Edge getEdgeInRange(Point p) 
		{
			// top
			if ( p.x >= x0+SELECTION_RADIUS && p.x <= x1-SELECTION_RADIUS && Math.abs( p.y - y0 ) <= SELECTION_RADIUS ) {
				return Edge.TOP;
			}
			
			// left
			if ( p.y >= y0+SELECTION_RADIUS && p.y <= y1-SELECTION_RADIUS && Math.abs( p.x - x0 ) <= SELECTION_RADIUS ) {
				return Edge.LEFT;
			}		
			
			// right
			if ( p.y >= y0+SELECTION_RADIUS && p.y <= y1-SELECTION_RADIUS && Math.abs( p.x - x1 ) <= SELECTION_RADIUS ) {
				return Edge.RIGHT;
			}		
			
			// bottom
			if ( p.x >= x0+SELECTION_RADIUS && p.x <= x1-SELECTION_RADIUS && Math.abs( p.y - y1 ) <= SELECTION_RADIUS ) {
				return Edge.BOTTOM;
			}			
			return null;
		}
		
		public Corner getCornerInRange(Point p) 
		{
			// top_left
			int dx = x0 - p.x;
			int dy = y0 - p.y;
			double dist = Math.sqrt( dx*dx + dy*dy );
			Corner result = Corner.TOP_LEFT;
			
			// top right
			dx = x1 - p.x;
			dy = y0 - p.y;
			double tmpDist = Math.sqrt( dx*dx + dy*dy );
			if ( tmpDist < dist ) {
				dist = tmpDist;
				result = Corner.TOP_RIGHT;
			}
			
			// bottom left 
			dx = x0 - p.x;
			dy = y1 - p.y;
			tmpDist = Math.sqrt( dx*dx + dy*dy );
			if ( tmpDist < dist ) {
				dist = tmpDist;
				result = Corner.BOTTOM_LEFT;
			}		
			
			// bottom right 
			dx = x1 - p.x;
			dy = y1 - p.y;
			tmpDist = Math.sqrt( dx*dx + dy*dy );
			if ( tmpDist < dist ) {
				dist = tmpDist;
				result = Corner.BOTTOM_RIGHT;
			}			
			
			if ( dist <= SELECTION_RADIUS ) {
				return result;
			}
			return null;
		}		
	}
	
	private void run(File imageFile) throws IOException 
	{
		run(ImageIO.read(imageFile ) );
	}

	private void run(BufferedImage imageFile) throws IOException 
	{
		image = imageFile;
		panel = new MyPanel(image);
		
		final JFrame frame = new JFrame();
		frame.getContentPane().setLayout( new GridBagLayout() );
		
		GridBagConstraints cnstrs = new GridBagConstraints();
		cnstrs.fill = GridBagConstraints.BOTH;
		cnstrs.weightx = 1.0d;
		cnstrs.weighty=1.0d;
		cnstrs.gridheight = GridBagConstraints.REMAINDER;
		cnstrs.gridwidth = GridBagConstraints.REMAINDER;
		frame.getContentPane().add( panel , cnstrs );
		frame.pack();
		frame.setVisible( true );
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE );
	}
	
	protected static enum Corner {
		TOP_LEFT,
		TOP_RIGHT,
		BOTTOM_LEFT,
		BOTTOM_RIGHT;
	}
	
	protected static enum Edge {
		TOP,LEFT,RIGHT,BOTTOM;
	}	
	
	protected static enum State {
		CORNER_DRAGGED,
		EDGE_DRAGGED,
		CREATING_SELECTION,
		MOVING_SELECTION,
		NONE;
	}
	
	protected static final class MyPanel extends JPanel {

		private final BufferedImage image;
		
		private Selection lastSelection;
		
		private Corner draggedCorner = null;
		private Edge draggedEdge = null;
		private State state=State.NONE;
		
		private Point dragStart;
		private Point dragEnd;
		private boolean rendered = false;
		private final MouseAdapter mouseAdapter = new MouseAdapter() 
		{
			public void mousePressed(java.awt.event.MouseEvent e) 
			{
				if ( e.getButton() == MouseEvent.BUTTON1 && state == State.NONE) 
				{
					Corner closestCorner= lastSelection != null ? lastSelection.getCornerInRange( e.getPoint() ) : null;
					if ( closestCorner!= null ) 
					{
						rendered = true;
						draggedCorner = closestCorner;
						System.out.println("DRAGGED CORNER: "+draggedCorner);
						state = State.CORNER_DRAGGED;
						return;
					}
					
					Edge closestEdge = lastSelection != null ? lastSelection.getEdgeInRange( e.getPoint() ) : null;
					if ( closestEdge != null ) 
					{
						rendered = true;
						draggedEdge = closestEdge;
						System.out.println("DRAGGED EDGE: "+draggedEdge);
						state = State.EDGE_DRAGGED;
						return;
					}

					if ( lastSelection != null && lastSelection.isCloseToCenter( e.getPoint() ) ) {
						rendered = true;
						state = State.MOVING_SELECTION;
						return;
					}
					
					System.out.println("--start");
					
					if ( dragStart != null && dragEnd != null && rendered ) 
					{
						renderSelection( getGraphics() );
					}
					dragStart = new Point(e.getPoint());
					dragEnd = null;
					state = State.CREATING_SELECTION;
					rendered = false;						
				}
			}
			
			public void mouseReleased(java.awt.event.MouseEvent e) 
			{
				if ( e.getButton() == MouseEvent.BUTTON1 && state != State.NONE ) 
				{
					switch(state) 
					{
						case CORNER_DRAGGED:
							if ( rendered ) {
								lastSelection.render( getGraphics() );
								rendered = ! rendered;
							}				
							lastSelection.moveCorner( draggedCorner , e.getPoint() );
							lastSelection.fixCoordinates();
							break;
						case MOVING_SELECTION:
							lastSelection.moveCenter( e.getPoint() );
							break;
						case EDGE_DRAGGED:
							if ( rendered ) {
								lastSelection.render( getGraphics() );
								rendered = ! rendered;
							}				
							lastSelection.moveEdge( draggedEdge , e.getPoint() );
							lastSelection.fixCoordinates();
							break;
						case CREATING_SELECTION:
							System.out.println("--end");

							if ( rendered ) 
							{
								renderSelection( getGraphics() );
							}
							dragEnd = new Point( e.getPoint() );	
							
							final Selection lastSelection = new Selection();
							
							lastSelection.x0 = Math.min(dragStart.x,dragEnd.x);
							lastSelection.y0 = Math.min(dragStart.y,dragEnd.y);
							lastSelection.x1 = Math.max(dragStart.x,dragEnd.x);
							lastSelection.y1 = Math.max(dragStart.y,dragEnd.y);
							
							MyPanel.this.lastSelection = lastSelection;
							break;
						default:
							throw new IllegalArgumentException("Invalid state: "+state);
					}
					
					state = State.NONE;			
					MyPanel.this.repaint();						
				}
			}
			
			public void mouseMoved(MouseEvent e) {
				
				if ( state == State.NONE && lastSelection != null && 
					( lastSelection.isCloseToCorner( e.getPoint() ) || lastSelection.isCloseToEdge( e.getPoint() ) ) )
				{
					setCursor( Cursor.getPredefinedCursor( Cursor.HAND_CURSOR ) );
				} else if ( state == State.NONE && lastSelection != null && lastSelection.isCloseToCenter( e.getPoint() ) ) {
					setCursor( Cursor.getPredefinedCursor( Cursor.MOVE_CURSOR ) );						
				} else {
					setCursor( Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR) );
				}
			}
			
			public void mouseDragged(MouseEvent e) 
			{
				if (state != State.NONE ) 
				{
					switch( state ) 
					{
						case CORNER_DRAGGED:
							if ( rendered ) {
								lastSelection.render( getGraphics() );
								rendered = !rendered;
							}
							lastSelection.moveCorner( draggedCorner , e.getPoint() );
							lastSelection.render( getGraphics() );
							rendered = !rendered;							
							break;
						case MOVING_SELECTION:
							if ( rendered ) {
								lastSelection.render( getGraphics() );
								rendered = !rendered;
							}
							lastSelection.moveCenter( e.getPoint() );
							lastSelection.render( getGraphics() );
							rendered = !rendered;								
							break;
						case EDGE_DRAGGED:
							if ( rendered ) {
								lastSelection.render( getGraphics() );
								rendered = !rendered;
							}							
							lastSelection.moveEdge( draggedEdge , e.getPoint() );
							lastSelection.render( getGraphics() );
							rendered = !rendered;							
							break;
						case CREATING_SELECTION:
							if ( rendered ) 
							{
								// clear old selection
								renderSelection( getGraphics() );
							}							
							dragEnd = new Point(e.getPoint());
							renderSelection( getGraphics() );
							break;
					}
				}
			}
		};
		
		private void renderSelection(Graphics g) 
		{
			g.setXORMode( Color.GREEN );
			int xMin = Math.min(dragStart.x,dragEnd.x);
			int yMin = Math.min(dragStart.y,dragEnd.y);
			int xMax = Math.max(dragStart.x,dragEnd.x);
			int yMax = Math.max(dragStart.y,dragEnd.y);
			System.out.println("Render: ("+xMin+","+yMin+") -> ("+xMax+","+yMax+")");
			g.drawRect( xMin,yMin , xMax - xMin , yMax - yMin );
			rendered = !rendered;
		}
		
		public MyPanel(BufferedImage image) {
			this.image = image;
			setMinimumSize( new Dimension(512,512 ) );
			setPreferredSize( new Dimension(512,512 ) );
			addMouseListener( this.mouseAdapter );
			addMouseMotionListener( this.mouseAdapter );
		}
		
		@Override
		protected void paintComponent(Graphics g) 
		{
			super.paintComponent(g);
			Graphics2D graphics = (Graphics2D) g;
			graphics.drawImage( image , 0 , 0 , getWidth() , getHeight() , null );
			if ( lastSelection != null ) {
				lastSelection.renderWithCenter(graphics);
			}
		}
	}
}
