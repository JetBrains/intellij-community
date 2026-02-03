xyzzy = False

def foo():
    if xyzzy:
        b<caret>ar = {}
    else:
        bar = []
    def x():
        for y in enumerate(bar):
            pass
