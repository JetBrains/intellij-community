class TestClass:
    def foo(self):
        print(0)

    @classmethod
    def <warning descr="Redeclared 'foo' defined above without usage">foo</warning>(cls):
        print(1)