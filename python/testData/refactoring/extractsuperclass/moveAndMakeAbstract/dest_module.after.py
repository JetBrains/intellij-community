from abc import abstractmethod
from abc import ABCMeta


class NewParent(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def foo_method(self):
        """
        Foo
        """
        pass