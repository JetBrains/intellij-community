
def deco(func, *args):
    return func

@deco  # <= Here is a false positive.
def myfunc(a, b):
    print(a, b)