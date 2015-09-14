def foo(*args):
    pass

a = ()
b = ()
foo(*a, <error descr="Duplicate *arg">*<caret>b</error>)


