from m1 import f, g, C, stub_only


def test_overloaded_function(x):
    g(<warning descr="Expected type 'dict[Any, Any]', got 'int' instead">f(10)</warning>)
    g(<warning descr="Expected type 'dict[Any, Any]', got 'str' instead">f('foo')</warning>)
    g(<warning descr="Expected type 'dict[Any, Any]', got 'int | str' instead">f(<warning descr="No overload of 'f' matches the arguments. Argument types: (dict[int, int]). Expected one of: (key: int), (key: str)">{1: 2}</warning>)</warning>)
    g(<warning descr="Expected type 'dict[Any, Any]', got 'int | str' instead">f(x)</warning>)


def test_overloaded_subscription_operator_parameters():
    c = C()
    print(c[10])
    print(c['foo'])
    print(c[<warning descr="No overload of '__getitem__' matches the arguments. Argument types: (dict[int, int]). Expected one of: (key: int), (key: str)">{1: 2}</warning>])


def test_overloaded_binary_operator_parameters():
    c = C()
    print(c + 10)
    print(c + 'foo')
    print(c + <warning descr="No overload of '__add__' matches the arguments. Argument types: (dict[int, int]). Expected one of: (other: int), (other: str)">{1: 2}</warning>)


def test_stub_only_function(x):
    g(<warning descr="Expected type 'dict[Any, Any]', got 'int' instead">stub_only(10)</warning>)
    g(<warning descr="Expected type 'dict[Any, Any]', got 'str' instead">stub_only('foo')</warning>)
    g(<warning descr="Expected type 'dict[Any, Any]', got 'int | str' instead">stub_only(x)</warning>)
    g(<warning descr="Expected type 'dict[Any, Any]', got 'int | str' instead">stub_only(<warning descr="No overload of 'stub_only' matches the arguments. Argument types: (dict[int, int]). Expected one of: (x: int), (x: str)">{1: 2}</warning>)</warning>)
