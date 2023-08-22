
def foo():
    pass

try:
    foo()
except NameError:
    pass
except <warning descr="'NameError', superclass of the exception class 'UnboundLocalError', has already been caught">Unbo<caret>undLocalError</warning>:
    pass