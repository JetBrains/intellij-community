from abc import abstractmethod, ABCMeta


class Abstract:
    __metaclass__ = ABCMeta

    @abstractmethod
    def foo0(self):
        pass

    @abstractmethod
    def foo1(self):
        pass

    def bar(self):
        pass


class F<caret>oo(Abstract):
    pass