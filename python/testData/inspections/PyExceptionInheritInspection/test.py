def test_basic():
    class A(object):
        pass

    class C(A):
        pass

    raise <warning descr="Exception doesn't inherit from base 'Exception' class">C()</warning>

    class D(C, Exception):
      pass

    raise D()


# PY-5811
def test_unknown_type_exception():
    class C(Unknown): pass

    raise C()


# PY-55086
def test_raising_base_exception():
    raise BaseException()