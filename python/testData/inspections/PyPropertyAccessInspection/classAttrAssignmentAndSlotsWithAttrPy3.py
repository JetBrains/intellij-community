# ValueError: 'attr' in __slots__ conflicts with class variable
# This is not responsibility of current inspection
class Foo(object):
    attr = 'baz'
    __slots__ = ['attr', 'bar']

Foo.attr = 'spam'
print(Foo.attr)

foo = Foo()
foo.attr = 'spam'
print(foo.attr)