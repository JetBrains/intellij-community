class Foo:
    def __call__(self, arg: int):
        return arg

bar = Foo()
bar.__call__(<warning descr="Expected type 'int', got 'LiteralString' instead">"s"</warning>)
bar(<warning descr="Expected type 'int', got 'LiteralString' instead">"s"</warning>)