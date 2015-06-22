from abc import ABCMeta
from abc import abstractmethod


class NewParent(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def foo_method(self):
        """
        Foo
        """
        pass