def test_class():
    class X:
        pass

    class <warning descr="Redeclared 'X' defined above without usage">X</warning>:
        pass

def test_function():
    def foo():
        pass

    def <warning descr="Redeclared 'foo' defined above without usage">foo</warning>():
        pass


# Top-level variable test
def TopLevelBoo():
    pass


<warning descr="Redeclared 'TopLevelBoo' defined above without usage">TopLevelBoo</warning> = 1
<warning descr="Redeclared 'TopLevelBoo' defined above without usage">TopLevelBoo</warning> = 2


class <warning descr="Redeclared 'TopLevelBoo' defined above without usage">TopLevelBoo</warning>:
    pass


def test_decorated_function(decorator):
    def foo():
        pass

    @decorator
    def foo():
        pass

    def <warning descr="Redeclared 'foo' defined above without usage">foo</warning>():
        pass


def test_local_variable():
    x = 1
    x = 2


def test_conditional(c):
    def foo():
        pass

    if c:
        def foo():
            pass

    try:
        def foo():
            pass
    except:
        pass


def test_while_loop(c):
    def foo():
        pass

    while c:
        def <warning descr="Redeclared 'foo' defined above without usage">foo</warning>():
            pass


class TestForLoopNoRedeclaraion:
    for foo in [1, 2, 3]:
        x = 1


class TestForLoopTarget:
    def foo():
        pass

    for <warning descr="Redeclared 'foo' defined above without usage">foo</warning> in [1, 2, 3]:
        x = 1


class TestForLoopBody:
    def foo():
        pass

    for _ in [1, 2, 3]:
        def <warning descr="Redeclared 'foo' defined above without usage">foo</warning>():
            pass


# PY-10839
class TestNestedComprehension:
    x = [[n for _ in []] for n in []]
    n = 2
