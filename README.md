## SYSC 3303 TFTP Project - Group 2
###### To build:
     Open cmd or terminal, change directory to project root
     
     javac -d build/ src/grouptwo/FileOperation.java
     javac -d build/ src/grouptwo/TFTPClient.java
     javac -d build/ src/grouptwo/TFTPIntHost.java
     javac -d build/ src/grouptwo/ClientConnectionThread.java src/grouptwo/TFTPServer.java
     
###### To launch:
     From project root,
     
     java -classpath . grouptwo.TFTPServer
     java -classpath . grouptwo.TFTPIntHost
     java -classpath . grouptwo.TFTPClient
