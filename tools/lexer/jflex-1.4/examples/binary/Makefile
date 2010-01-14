
JAVA  = java
JAVAC = javac
JFLEX = jflex

# -------------------------------------------------

all: run

run: compile
	$(JAVA) BinaryLexer *

compile: BinaryLexer.class StraightStreamReader.class

clean:
	rm -f *~ *.class BinaryLexer.java

%.class: %.java
	$(JAVAC) $^

BinaryLexer.java: binary.flex
	$(JFLEX) $^
