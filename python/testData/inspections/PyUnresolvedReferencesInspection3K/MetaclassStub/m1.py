class M(type):
    def foo(cls):
        pass


class C(metaclass=M):
    pass
