def test_property():
    class C(object):
        @property
        def f(self):
            # type: () -> C
            return C()

        @f.setter
        def f(self, value):
            # type: (C) -> None
            pass

        @f.deleter
        def f(self):
            # type: () -> None
            pass

    c = C()
    assert isinstance(c.f, C)
    assert c.f is not None
    assert c.f.f is not None
    assert isinstance(C.f, property)
    assert C.f.fget(c) is not None
    assert C.f.fget(c).f
    assert C.f.fset(c, c) is None
    assert C.f.fdel(c) is None


def test_object_module():
    class C(object):
        pass

    c = C()

    assert c.__module__ is not None
    assert c.__module__.strip() == c.__module__
    assert C.__module__ is not None
    assert object.__module__ is not None
    assert type.__module__ is not None


def test_object_reduce():
    class C(object):
        pass

    c = C()
    assert isinstance(c.__reduce__(), (str, tuple))
    assert isinstance(c.__reduce_ex__(), (str, tuple))
