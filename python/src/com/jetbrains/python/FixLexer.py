import os, os.path
if os.path.exists("_PythonLexerBad.java"): os.unlink("_PythonLexerBad.java")
os.rename("_PythonLexer.java", "_PythonLexerBad.java")
f = open("_PythonLexerBad.java", "r")
out = open("_PythonLexer.java", "w")
for line in f.readlines():
    i = line.find("zzBufferL[")
    if i >= 0:
        line = line [0:i-1] + "zzBufferL.charAt(" + line [i+10:-3] + ");\n"
    out.write(line)
f.close()
out.close()
os.unlink("_PythonLexerBad.java")
