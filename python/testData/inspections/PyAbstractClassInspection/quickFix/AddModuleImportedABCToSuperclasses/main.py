import abc

class A1(metaclass=abc.ABCMeta):
    @abc.abstractmethod
    def foo(self):
        pass


class A<caret>2(A1):
    pass
