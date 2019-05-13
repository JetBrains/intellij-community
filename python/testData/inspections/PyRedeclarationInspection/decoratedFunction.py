def test_decorated_function(decorator):
    def foo():
        pass

    @decorator
    def foo():
        pass

    def <warning descr="Redeclared 'foo' defined above without usage">foo</warning>():
        pass