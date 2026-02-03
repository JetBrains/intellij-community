class Super:
    def __init__(self):
        pass


class Target(Super):
    def __<caret>init__(self, foo, bar):
        super().__init__()
        print(foo)


class Sub(Target):
    def __init__(self, foo, extra):
        super().__init__(foo, 42)
        print(extra)