
a = ""

def b(c):
    pass


<warning descr="'LiteralString' object is not callable">@<caret>a</warning>
@b
def foo():
    pass

