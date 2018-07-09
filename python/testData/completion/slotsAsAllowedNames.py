class A(object):
    __slots__ = ['foo']

    def __init__(self):
        self.bar = 1


A().ba<caret>


class B:
    __slots__ = ['foo']

    def __init__(self):
        self.bar = 1


B().ba<caret>


class C(object):
    __slots__ = ['foo', '__dict__']

    def __init__(self):
        self.bar = 1


C().ba<caret>