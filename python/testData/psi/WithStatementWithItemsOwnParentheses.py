with (foo()):
    pass
with ((foo())):
    pass
with (foo()), (foo()):
    pass
with (foo()) as bar:
    pass
with ((foo()) as bar):
    pass
with (bar := foo()):
    pass
with (bar := foo()) as bar:
    pass