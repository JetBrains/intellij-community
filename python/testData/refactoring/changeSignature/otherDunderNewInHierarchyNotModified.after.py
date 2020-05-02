class Super:
    def __new__(cls):
        return super().__new__(cls)


class Target(Super):
    def __new__(cls, foo, bar):
        print(foo)
        return super().__new__(cls)


class Sub(Target):
    def __new__(cls, foo, extra):
        print(extra)
        return super().__new__(cls, foo, 42)
