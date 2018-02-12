class TestClass:
    @classmethod
    def foo(cls):
        print(0)

    @classmethod
    def <warning descr="Redeclared 'foo' defined above without usage">foo</warning>(cls):
        print(1)