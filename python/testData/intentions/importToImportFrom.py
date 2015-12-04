import __builtin<caret>__ as b

quotient, rem = b.divmod(42, 3)

// PY-11074
class MyClass(object):
    @b.staticmethod
    def method():
        pass