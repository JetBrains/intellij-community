from m1 import f, g, C, stub_only


def test_overloaded_function(x):
    g(<warning descr="Expected type 'dict', got 'int' instead">f(10)</warning>)
    g(<warning descr="Expected type 'dict', got 'str' instead">f('foo')</warning>)
    g(<warning descr="Expected type 'dict', got 'Union[int, str]' instead">f(<warning descr="Unexpected type(s):(Dict[int, int])Possible types:(str)(int)">{1: 2}</warning>)</warning>)
    g(<warning descr="Expected type 'dict', got 'Union[int, str]' instead">f(x)</warning>)


def test_overloaded_subscription_operator_parameters():
    c = C()
    print(c[10])
    print(c['foo'])
    print(c[<warning descr="Unexpected type(s):(Dict[int, int])Possible types:(int)(str)">{1: 2}</warning>])


def test_overloaded_binary_operator_parameters():
    c = C()
    print(c + 10)
    print(c + 'foo')
    print(c + <warning descr="Unexpected type(s):(Dict[int, int])Possible types:(int)(str)">{1: 2}</warning>)


def test_stub_only_function(x):
    g(<warning descr="Expected type 'dict', got 'int' instead">stub_only(10)</warning>)
    g(<warning descr="Expected type 'dict', got 'str' instead">stub_only('foo')</warning>)
    g(<warning descr="Expected type 'dict', got 'Union[int, str]' instead">stub_only(x)</warning>)
    g(<warning descr="Expected type 'dict', got 'Union[int, str]' instead">stub_only(<warning descr="Unexpected type(s):(Dict[int, int])Possible types:(str)(int)">{1: 2}</warning>)</warning>)
