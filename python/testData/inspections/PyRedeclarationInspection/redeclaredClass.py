def test_class():
    class X:
        pass

    class <warning descr="Redeclared 'X' defined above without usage">X</warning>:
        pass