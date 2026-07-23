import abc as a

class A1(metaclass=a.ABCMeta):
    @a.abstractmethod
    def foo(self):
        pass

class A<caret>2(A1):
    pass
