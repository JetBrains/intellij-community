# uses JFlex >= 1.3.2, and CUP >= 0.10j
#
# targets:
#
# make all                                                            
#    generates lexer, and parser, and compiles all *.java files
#                                                                     
# make run (or just: make)                                           
#    starts the program on a test example
#                                                                     



.SUFFIXES: $(SUFFIXES) .class .java

.java.class:
	javac $*.java

FILE=	Yylex.java      parser.java    sym.java \
	SymTab.java	AST.java \
	Tnumber.java	Tident.java	Texp.java	\
	Tfun.java	Texpinfix.java	Tuminus.java	\
	Tboolexp.java	Tifthenelse.java		\
	Tdekl.java	Tdekllist.java			\
	Tparlist.java   Texplist.java	Tprogram.java	\
	Main.java \
	SymtabEntry.java STEfun.java	STEvar.java


run: all
	java Main < example.as

all: Yylex.java parser.java $(FILE:java=class)

clean:
	rm -f *.class *~ *.bak Yylex.java parser.java sym.java

Yylex.java: scanner.flex
	jflex scanner.flex

parser.java: parser.cup
	java java_cup.Main -interface < parser.cup
