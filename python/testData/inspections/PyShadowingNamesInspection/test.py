global_foo = 1


def test_import_builtin_names():
    import float
    from foo import float
    from bar import baz as <weak_warning descr="Shadows built-in name 'float'">float</weak_warning>


def test_builtin_function_parameters():
    def test1(x, _, <weak_warning descr="Shadows built-in name 'len'">len</weak_warning>, <weak_warning descr="Shadows built-in name 'file'">file</weak_warning>=None):
        pass


def test_builtin_function_name():
    def <weak_warning descr="Shadows built-in name 'list'">list</weak_warning>():
        pass


def test_builtin_assignment_targets():
    foo = 2
    <weak_warning descr="Shadows built-in name 'list'">list</weak_warning> = []
    for <weak_warning descr="Shadows built-in name 'int'">int</weak_warning> in range(10):
        print(int)
    <weak_warning descr="Shadows built-in name 'range'">range</weak_warning> = []
    <weak_warning descr="Shadows built-in name 'list'">list</weak_warning>, _ = (1, 2)
    return [int for <weak_warning descr="Shadows built-in name 'int'">int</weak_warning> in range(10)]


def test_builtin_class_name():
    class <weak_warning descr="Shadows built-in name 'list'">list</weak_warning>(object):
        pass


def test_builtin_method_name():
    class C:
        def list(self):
            pass


# PY-8646
def test_builtin_qualified_name():
    test1.range = float()

    class C:
        def foo(self):
            self.list = []


# PY-10164
def test_builtin_class_attribute():
    class C:
        id = 1


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


def test_comprehensions():
    print(x for x in range(10))
    print([y for y in range(10)])
    def f(x, <weak_warning descr="Shadows name 'y' from outer scope">y</weak_warning>):
        pass
