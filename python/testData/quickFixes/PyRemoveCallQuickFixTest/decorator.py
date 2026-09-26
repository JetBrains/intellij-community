
a = ""

def b(c):
    pass


<warning descr="'Literal[\"\"]' object is not callable">@<caret>a</warning>
@b
def foo():
    pass

