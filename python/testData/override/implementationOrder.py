from abc import abstractmethod


class Abstract:
    @abstractmethod
    def foo(self):
        pass

    @abstractmethod
    def bar(self):
        pass


class Impl(Abstract):
    pass