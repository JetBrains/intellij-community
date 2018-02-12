def test_methodtype():
    import types


    class A:
        def foo(self):
            return "ok"


    assert types.MethodType(A.foo, A())() == "ok"