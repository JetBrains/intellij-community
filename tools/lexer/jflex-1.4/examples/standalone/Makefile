all: run

run: Subst.class
	java Subst sample.inp

Subst.class: Subst.java
	javac Subst.java

Subst.java: standalone.flex
	jflex standalone.flex

clean:
	rm -rf *.class *~ *.java
