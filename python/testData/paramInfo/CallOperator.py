class Foo:
    def __call__(self, arg: int):
        return arg

bar = Foo()
bar.__call__(<arg1>)
bar(<arg2>)