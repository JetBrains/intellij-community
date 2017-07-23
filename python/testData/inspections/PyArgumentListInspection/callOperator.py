class Foo:
    def __call__(self, arg: int):
        return arg

bar = Foo()
bar.__call__(<warning descr="Parameter 'arg' unfilled">)</warning>
bar(<warning descr="Parameter 'arg' unfilled">)</warning>