class TestClass:
    def foo(self):
        print(0)

    @staticmethod
    def <warning descr="Redeclared 'foo' defined above without usage">foo</warning>():
        print(1)