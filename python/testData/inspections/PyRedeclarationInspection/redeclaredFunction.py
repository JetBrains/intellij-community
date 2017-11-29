def test_function():
    def foo():
        pass

    def <warning descr="Redeclared 'foo' defined above without usage">foo</warning>():
        pass