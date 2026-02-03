from abc import ABCMeta, abstractmethod


class AbstractClass(metaclass=ABCMeta):
    @abstractmethod
    def foo(self):
        ...


def bar(cls: type[AbstractClass]):
    return cls().foo()


AC = AbstractClass

<warning descr="Cannot instantiate abstract class 'AbstractClass'">AC()</warning>
