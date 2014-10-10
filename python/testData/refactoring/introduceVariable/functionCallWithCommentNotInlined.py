import subprocess as sp

print(sp.c<caret>heck_output(
    args=['python', '-c', 'print("Spam")'],
    # read errors too
    stderr=sp.STDOUT
))