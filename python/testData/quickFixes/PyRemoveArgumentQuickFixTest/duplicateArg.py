def foo(*args):
    pass

a = ()
b = ()
foo(*a, <warning descr="Duplicate *arg">*<caret>b</warning>)


