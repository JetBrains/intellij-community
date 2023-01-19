if a; then b; fi
if a; then b
fi
if a; then b; else c; fi
if a; then b
else c; fi
if a; then b
else c;
fi
#this is not an if command with a parsed else, there is no semicolon after the first command
#it can be parsed but must make sure that the else is not parsed as keyword
if echo a; then echo b else echo c; fi
