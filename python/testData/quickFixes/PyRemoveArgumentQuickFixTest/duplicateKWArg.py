
def foo(**args):
    pass

a = {}
b = {}
foo(**a, <error descr="Python version 2.7 does not allow duplicate **expressions">**<caret>b</error>)


