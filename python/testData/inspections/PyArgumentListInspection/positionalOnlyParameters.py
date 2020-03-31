def foo(a, /, b, *args, c, **kwargs):
    print(a, b, args, c, kwargs)

# valid
# b as positional
foo(1, 2, c=3)
foo(1, 2, 3, c=3)
foo(1, 2, 3, c=3, d=4)

# b as keyword
foo(1, b=2, c=3)
foo(1, c=3, b=2)

# invalid
foo(<warning descr="Parameter 'a' unfilled"><warning descr="Parameter 'b' unfilled"><warning descr="Parameter 'c' unfilled">)</warning></warning></warning>
foo(c=3<warning descr="Parameter 'a' unfilled"><warning descr="Parameter 'b' unfilled">)</warning></warning>
foo(b=2, c=3<warning descr="Parameter 'a' unfilled">)</warning>
foo(a=1, b=2, c=3<warning descr="Parameter 'a' unfilled">)</warning>


def foo2(a, /, b=2, *args, c, **kwargs):
    print(a, b, args, c, kwargs)

# valid
# b as positional
foo2(1, 2, c=3)
foo2(1, 2, 3, c=3)
foo2(1, 2, 3, c=3, d=4)

# b as keyword
foo2(1, b=2, c=3)
foo2(1, c=3, b=2)

# no b
foo2(1, c=3)

# invalid
foo2(<warning descr="Parameter 'a' unfilled"><warning descr="Parameter 'c' unfilled">)</warning></warning>
foo2(c=3<warning descr="Parameter 'a' unfilled">)</warning>
foo2(b=2, c=3<warning descr="Parameter 'a' unfilled">)</warning>
foo2(a=1, b=2, c=3<warning descr="Parameter 'a' unfilled">)</warning>


def foo3(a=1, /, b=2, *args, c, **kwargs):
    print(a, b, args, c, kwargs)

# valid
# b as positional
foo3(1, 2, c=3)
foo3(1, 2, 3, c=3)
foo3(1, 2, 3, c=3, d=4)

# b as keyword
foo3(1, b=2, c=3)
foo3(1, c=3, b=2)

# no b
foo3(1, c=3)

# no a, b as positional
foo3(2, c=3)
foo3(2, 3, c=3)
foo3(2, 3, c=3, d=4)

# no a, b as keyword
foo3(b=2, c=3)
foo3(c=3, b=2)

# no a, no b
foo3(c=3)
foo3(a=1, b=2, c=3)  # a goes to kwargs

# invalid
foo3(<warning descr="Parameter 'c' unfilled">)</warning>


def foo4(a=1, /, b=2, *args, c):
    print(a, b, args, c)

# invalid
foo4(<warning descr="Unexpected argument">a=1</warning>, b=2, c=3)
