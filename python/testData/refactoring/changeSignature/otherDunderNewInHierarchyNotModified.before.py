class Super:
    def __new__(cls):
        return super().__new__(cls)


class Target(Super):
    def __n<caret>ew__(cls, foo):
        print(foo)
        return super().__new__(cls)


class Sub(Target):
    def __new__(cls, foo, extra):
        print(extra)
        return super().__new__(cls, foo)
