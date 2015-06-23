from abc import abstractmethod, ABCMeta


class NewParent(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def foo_method(self):
        """
        Foo
        """
        pass