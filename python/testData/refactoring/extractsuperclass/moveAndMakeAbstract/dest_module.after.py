from abc import ABCMeta, abstractmethod


class NewParent(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def foo_method(self):
        """
        Foo
        """
        pass