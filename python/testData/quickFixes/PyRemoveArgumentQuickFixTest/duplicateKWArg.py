
def foo(**args):
    pass

a = {}
b = {}
foo(**a, <error descr="Python versions < 3.5 do not allow duplicate **expressions">**<caret>b</error>)


