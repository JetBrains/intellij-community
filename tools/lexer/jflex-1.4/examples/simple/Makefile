JAVA=java
JAVAC=javac
JFLEX=jflex
CUP=$(JAVA) java_cup.Main <

all: test

test: output.txt
	@(diff output.txt output.good && echo "Test OK!") || echo "Test failed!"

output.txt: Yylex.class test.txt
	$(JAVA) Yylex test.txt > output.txt

Yylex.class: Yylex.java Yytoken.java Utility.java

%.class: %.java
	$(JAVAC) $^

Yylex.java: simple.flex
	$(JFLEX) simple.flex

clean:
	rm -f Yylex.java output.txt *.class *~
