class ClassMethodMultipleDecorators:
    @deco
    @classmethod
    @deco2(1, 2)
    def foo(cls, other): pass

ClassMethodMultipleDecorators.foo(1)
