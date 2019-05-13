from abc import abstractproperty as alias, ABCMeta

class MyAbsBase(object):
    __metaclass__ = ABCMeta

    @alias
    def count(self):   # <-  false positive here
        return