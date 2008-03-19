set JAVA_HOME="C:\Program Files\Java\jdk1.5.0_12"
call C:\Src\jflex-1.4.1\bin\jflex.bat --table --skel idea-skeleton Python.flex
python FixLexer.py

