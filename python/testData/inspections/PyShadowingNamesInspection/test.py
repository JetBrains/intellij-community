def test_import_builtin_names():
    import float
    from foo import float
    from bar import baz as <warning descr="Shadows built-in name 'float'">float</warning>


def test_builtin_function_parameters():
    def test1(x, _, <warning descr="Shadows built-in name 'len'">len</warning>, <warning descr="Shadows built-in name 'file'">file</warning>=None):
        pass


def test_builtin_function_name():
    def <warning descr="Shadows built-in name 'list'">list</warning>():
        pass


def test_builtin_assignment_targets():
    foo = 2
    <warning descr="Shadows built-in name 'list'">list</warning> = []
    for <warning descr="Shadows built-in name 'int'">int</warning> in range(10):
        print(int)
    <warning descr="Shadows built-in name 'range'">range</warning> = []
    <warning descr="Shadows built-in name 'list'">list</warning>, _ = (1, 2)
    return [int for <warning descr="Shadows built-in name 'int'">int</warning> in range(10)]


def test_builtin_class_name():
    class <warning descr="Shadows built-in name 'list'">list</warning>(object):
        pass


def test_builtin_method_name():
    class C:
        def <warning descr="Shadows built-in name 'list'">list</warning>(self):
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
        <warning descr="Shadows built-in name 'id'">id</warning> = 1


def test_outer_function():
    foo = 1
    def bar():
        <warning descr="Shadows name 'foo' from outer scope">foo</warning>, <warning descr="Shadows name 'bar' from outer scope">bar</warning> = 1, 2
    def baz(<warning descr="Shadows name 'foo' from outer scope">foo</warning>, <warning descr="Shadows name 'bar' from outer scope">bar</warning>, <warning descr="Shadows name 'baz' from outer scope">baz</warning>):
        pass
    def nested():
        def <warning descr="Shadows name 'baz' from outer scope">baz</warning>(<warning descr="Shadows name 'foo' from outer scope">foo</warning>):
            <warning descr="Shadows name 'bar' from outer scope">bar</warning> = 1


def test_outer_class():
    baz = 1
    quux = 2
    spam = 3
    class C(object):
        def foo(self):
            def foo():
                def <warning descr="Shadows name 'bar' from outer scope">bar</warning>():
                    class <warning descr="Shadows name 'C' from outer scope">C</warning>:
                        def <warning descr="Shadows name 'baz' from outer scope">baz</warning>(self):
                            pass
            def bar():
                pass

            <warning descr="Shadows name 'baz' from outer scope">baz</warning> = 2

        def <warning descr="Shadows name 'spam' from outer scope">spam</warning>(self):
            pass

        <warning descr="Shadows name 'quux' from outer scope">quux</warning> = 1
