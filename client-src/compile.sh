javac -classpath . com/botbox/util/*.java
javac -classpath . se/sics/tac/util/*.java
javac -classpath . se/sics/tac/aw/*.java
javac -classpath . se/bth/ooseven/*.java

jar cfm tacagent.jar AWManifest.txt com/botbox/util/*.class se/sics/tac/aw/*.class se/sics/tac/util/*.class se/bth/ooseven/*.class
