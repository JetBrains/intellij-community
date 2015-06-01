import abc


class A1(object):
    __metaclass__ = abc.ABCMeta

    @abc.abstractmethod
    def m1(self):
        pass


class A2(A1):
    @abc.abstractmethod
    def m2(self):
        pass