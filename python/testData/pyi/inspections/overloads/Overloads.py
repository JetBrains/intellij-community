from m1 import f, g, C, stub_only, Gen


def test_overloaded_function(x):
    g(<warning descr="Expected type 'dict', got 'int' instead">f(10)</warning>)
    g(<warning descr="Expected type 'dict', got 'str' instead">f('foo')</warning>)
    g(<warning descr="Expected type 'dict', got 'Union[int, str]' instead">f(<warning descr="Expected type 'int', got 'Dict[int, int]' instead">{1: 2}</warning>)</warning>)
    g(<warning descr="Expected type 'dict', got 'Union[int, str]' instead">f(x)</warning>)


def test_overloaded_subscription_operator_parameters():
    c = C()
    print(c[10])
    print(c['foo'])
    print(c[<warning descr="Expected type 'int', got 'Dict[int, int]' instead">{1: 2}</warning>])


def test_overloaded_binary_operator_parameters():
    c = C()
    print(c + 10)
    print(c + 'foo')
    print(c + <warning descr="Expected type 'int', got 'Dict[int, int]' instead">{1: 2}</warning>)


def test_stub_only_function(x):
    g(<warning descr="Expected type 'dict', got 'int' instead">stub_only(10)</warning>)
    g(<warning descr="Expected type 'dict', got 'str' instead">stub_only('foo')</warning>)
    g(<warning descr="Expected type 'dict', got 'Union[int, str]' instead">stub_only(x)</warning>)
    g(<warning descr="Expected type 'dict', got 'Union[int, str]' instead">stub_only(<warning descr="Expected type 'int', got 'Dict[int, int]' instead">{1: 2}</warning>)</warning>)


def tset_overloaded_generics(x):
    g(<warning descr="Expected type 'dict', got 'int' instead">Gen(10).get(10, 10)</warning>)
    g(Gen(10).get(10, <weak_warning descr="Expected type 'int' (matched generic type 'TypeVar('T')'), got 'str' instead">'foo'</weak_warning>))
    g(Gen('foo').get(10, <weak_warning descr="Expected type 'str' (matched generic type 'TypeVar('T')'), got 'int' instead">10</weak_warning>))
    g(<warning descr="Expected type 'dict', got 'str' instead">Gen('foo').get(10, 'foo')</warning>)
