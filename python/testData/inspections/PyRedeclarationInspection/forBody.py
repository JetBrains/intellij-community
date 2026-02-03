class TestForLoopBody:
    def foo():
        pass

    for _ in [1, 2, 3]:
        def <warning descr="Redeclared 'foo' defined above without usage">foo</warning>():
            pass