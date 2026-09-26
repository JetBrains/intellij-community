class Super:
    def __init__(self):
        pass


class Target(Super):
    def __<caret>init__(self, foo):
        super().__init__()
        print(foo)


class Sub(Target):
    def __init__(self, foo, extra):
        super().__init__(foo)
        print(extra)