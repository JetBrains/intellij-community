global_foo = 1


def test_outer_function():
    foo = 1
    def bar():
        <weak_warning descr="Shadows name 'foo' from outer scope">foo</weak_warning>, <weak_warning descr="Shadows name 'bar' from outer scope">bar</weak_warning> = 1, 2
    def baz(<weak_warning descr="Shadows name 'foo' from outer scope">foo</weak_warning>, <weak_warning descr="Shadows name 'bar' from outer scope">bar</weak_warning>, <weak_warning descr="Shadows name 'baz' from outer scope">baz</weak_warning>):
        pass
    def nested():
        def <weak_warning descr="Shadows name 'baz' from outer scope">baz</weak_warning>(<weak_warning descr="Shadows name 'foo' from outer scope">foo</weak_warning>):
            <weak_warning descr="Shadows name 'bar' from outer scope">bar</weak_warning> = 1


def test_outer_class():
    baz = 1
    quux = 2
    spam = 3
    class C(object):
        def foo(self):
            def foo():
                def <weak_warning descr="Shadows name 'bar' from outer scope">bar</weak_warning>():
                    class <weak_warning descr="Shadows name 'C' from outer scope">C</weak_warning>:
                        def baz(self):
                            pass
            def bar():
                pass

            <weak_warning descr="Shadows name 'baz' from outer scope">baz</weak_warning> = 2

        def spam(self):
            pass

        quux = 1


def test_outer_global():
    global global_foo
    global_foo = 2


def test_outer_comprehensions():
    print(x for x in range(10))
    print([y for y in range(10)])
    def f(x, <weak_warning descr="Shadows name 'y' from outer scope">y</weak_warning>):
        pass
