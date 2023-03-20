with (foo()):
    pass
with (foo(),):
    pass
with (foo() as bar):
    pass
with (foo() as bar,):
    pass
with (foo(), foo()):
    pass
with (foo() as bar, foo()):
    pass
with (foo(), foo() as bar):
    pass
with (foo() as bar, foo() as baz):
    pass