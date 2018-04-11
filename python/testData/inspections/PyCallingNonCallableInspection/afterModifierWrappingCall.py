def f(x):
    print(x)


class A:
    fn = staticmethod(f)

    def do(self, x):
        self.fn(x)