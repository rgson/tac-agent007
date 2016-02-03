#!/bin/sh

CP=.:lib/org.mortbay.jetty.jar:lib/javax.servlet.jar
javac -classpath $CP com/botbox/util/*.java
javac -classpath $CP com/botbox/html/*.java
javac -classpath $CP se/sics/isl/inet/*.java
javac -classpath $CP se/sics/isl/util/*.java
javac -classpath $CP se/sics/tac/util/*.java
javac -classpath $CP se/sics/tac/log/*.java
javac -classpath $CP se/sics/tac/is/*.java
javac -classpath $CP se/sics/tac/server/*.java
javac -classpath $CP se/sics/tac/server/classic/*.java
javac -classpath $CP se/sics/tac/line/*.java
javac -classpath $CP se/sics/tac/solver/*.java
javac -target 1.1 se/sics/tac/applet/*.java

jar cfm tacserver.jar manifest/TACServerManifest.txt com/botbox/util/*.class com/botbox/html/*.class se/sics/isl/inet/*.class se/sics/isl/util/*.class se/sics/tac/util/*.class se/sics/tac/log/*.class se/sics/tac/server/*.class se/sics/tac/server/classic/*.class se/sics/tac/line/*.class se/sics/tac/solver/*.class

jar cfm infoserver.jar manifest/InfoServerManifest.txt com/botbox/util/*.class com/botbox/html/*.class se/sics/isl/inet/*.class se/sics/isl/util/*.class se/sics/tac/util/*.class se/sics/tac/log/*.class se/sics/tac/is/*.class se/sics/tac/line/*.class se/sics/tac/solver/*.class

jar cf public_html/code/tacapplet.jar se/sics/tac/applet/*.class
