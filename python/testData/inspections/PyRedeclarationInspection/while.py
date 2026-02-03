def test_while_loop(c):
    def foo():
        pass

    while True:
        def <warning descr="Redeclared 'foo' defined above without usage">foo</warning>():
            pass