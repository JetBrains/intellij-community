class Foo(object):
    attr = 'baz'
    __slots__ = [<warning descr="'attr' in __slots__ conflicts with class variable">'attr'</warning>, 'bar']

Foo.attr = 'spam'
print(Foo.attr)

foo = Foo()
foo.attr = 'spam'
print(foo.attr)