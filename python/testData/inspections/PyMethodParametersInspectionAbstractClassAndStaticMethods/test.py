import abc


class A:
    @abc.abstractclassmethod
    def foo(cls):
        pass

    @abc.abstractstaticmethod
    def bar():
        pass
