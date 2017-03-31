class TestForLoopTarget:
    def foo():
        pass

    for <warning descr="Redeclared 'foo' defined above without usage">foo</warning> in [1, 2, 3]:
        x = 1