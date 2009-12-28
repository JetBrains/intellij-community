JAVA=java
JAVAC=javac
JFLEX=jflex
CUP=$(JAVA) java_cup.Main <

all: test

test: output.txt
	@(diff output.txt output.good && echo "Test OK!") || echo "Test failed!"

output.txt: Main.class test.txt
	$(JAVA) Main test.txt > output.txt

Main.class: Main.java Lexer.java parser.java

%.class: %.java
	$(JAVAC) $^

Lexer.java: lcalc.flex
	$(JFLEX) lcalc.flex

parser.java: ycalc.cup
	$(CUP) ycalc.cup

clean:
	rm -f parser.java Lexer.java sym.java output.txt *.class *~
