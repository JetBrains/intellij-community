import builtins as b

quotient, rem = b.divmod(42, 3)
b.divmod

class MyClass(object):
    @b.stati<caret>cmethod
    @b.staticmethod
    def method():
        pass