from __future__ import print_function
import shlex

lexer = shlex.shlex("a b c")  # breakpoint
for tok in lexer:
    print(tok)
