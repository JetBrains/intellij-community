from __builtin__ import staticmethod, divmod

quotient, rem = divmod(42, 3)

// PY-11074
class MyClass(object):
    @staticmethod
    def method():
        pass