def test_class():
    class X:
        pass

    class <warning descr="Shadows a class with the same name defined above">X</warning>:
        pass

def test_function():
    def foo():
        pass

    def <warning descr="Shadows a function with the same name defined above">foo</warning>():
        pass


# Top-level variable test
def TopLevelBoo():
    pass


<warning descr="Shadows a function with the same name defined above">TopLevelBoo</warning> = 1
<warning descr="Shadows a variable with the same name defined above">TopLevelBoo</warning> = 2


class <warning descr="Shadows a variable with the same name defined above">TopLevelBoo</warning>:
    pass


def test_decorated_function(decorator):
    def foo():
        pass

    @decorator
    def foo():
        pass

    def <warning descr="Shadows a function with the same name defined above">foo</warning>():
        pass


def test_local_variable():
    x = 1
    x = 2
