class Foo(object):
    __slots__ = [<warning descr="'foo' in __slots__ conflicts with class variable">'foo'</warning>, 'bar']
    foo = 1