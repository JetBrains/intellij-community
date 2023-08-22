class Foo:
    def method(self, arg):
        pass


if undef:
    Bar = Foo
else:
    Bar = undef

Bar.method(Foo(), arg=undef)