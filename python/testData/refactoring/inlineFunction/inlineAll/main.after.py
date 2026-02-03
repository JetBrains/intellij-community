def foo(arg):
    local = 1
    if arg:
        another = 2
    else:
        another = 3
    return local


def bar():
    x = 1
    local = 1
    if x:
        another = 2
    else:
        another = 3
    res = local


def baz():
    y = 2
    local = 1
    if y:
        another = 2
    else:
        another = 3
    res = local


z = 1
local = 1
if z:
    another = 2
else:
    another = 3
res = local
