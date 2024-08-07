import sys

if True:
    if sys.version_info >= (3,):
        if sys.version_info < (3, 12):
            foo = 23
    else:
        bar = -1