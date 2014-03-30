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
