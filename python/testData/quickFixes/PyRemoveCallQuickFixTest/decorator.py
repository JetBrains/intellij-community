
a = ""

def b(c):
    pass


<warning descr="'str' object is not callable">@<caret>a</warning>
@b
def foo():
    pass

