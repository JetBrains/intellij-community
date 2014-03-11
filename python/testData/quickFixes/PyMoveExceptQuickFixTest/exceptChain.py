
def foo():
    pass


try:
    foo()
except NameError:
    pass
except Exception:
    pass
except <warning descr="'NameError', superclass of exception class 'UnboundLocalError', has already been caught">UnboundLocalE<caret>rror</warning>:
    pass