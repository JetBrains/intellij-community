
def foo():
    pass

try:
    foo()
except UnboundLocalError:
    pass
except NameError:
    pass
