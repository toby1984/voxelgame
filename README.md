voxelgame
=========

A simple voxel engine that uses 3D simplex noise to procedurally generate an infinite,random terrain. You need to have a OpenGL 2.0+ compatible graphics card and appropriate drivers installed.

Use the mouse and WASD to navigate, right-click to place blocks and left-click to 'shoot'.

<img src="http://www2.code-sourcery.de/blog/wp-content/uploads/2014/10/mc_clone.png" />

Building
------

Note that you will first need to adjust some filesystem paths in de.codesourcery.voxelgame.core.Constants to actually
run the program (sorry, I was lazy and used hard-coded values) , namely the path to the assets folder and the folder for storing chunk data).

You need to have JDK >=1.7 and Maven >= 2.2.1 installed to build & run the program:

   mvn clean package && java -jar desktop/target/desktop-1.0-SNAPSHOT-jar-with-dependencies.jar
