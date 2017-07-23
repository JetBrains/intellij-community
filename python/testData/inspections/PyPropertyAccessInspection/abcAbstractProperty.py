from abc import ABCMeta, abstractproperty

class Upper(object):
    __metaclass__ = ABCMeta

    @abstractproperty
    def prop(self):
        pass

    @prop.setter
    def prop(self, prop):
        pass

    def foo(self):
        print self.prop