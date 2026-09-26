class SomeClass:
    @classmethod
    def f<caret>oo(cls, arg1, arg2):
        pass


SomeClass.foo(1, 2)
SomeClass().foo(1, 2)