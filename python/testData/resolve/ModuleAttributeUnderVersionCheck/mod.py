import sys

if True:
    if sys.version_info >= (3,):
        if sys.version_info >= (3, 10) and sys.version_info < (3, 12):
            foo = 23
        if sys.version_info < (3, 11) and (sys.version_info < (3, 5) or sys.version_info > (3, 7)):
            buz = 23
    else:
        bar = -1