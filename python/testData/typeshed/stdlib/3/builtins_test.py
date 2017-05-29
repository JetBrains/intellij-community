def test_zip():
    assert(list(zip([1])) == [(1,)])
    assert(list(zip([1], [2])) == [(1, 2,)])
    assert(list(zip([1], [2], [3])) == [(1, 2, 3)])
    assert(list(zip([1], [2], [3], [4])) == [(1, 2, 3, 4)])
    assert(list(zip([1], [2], [3], [4], [5])) == [(1, 2, 3, 4, 5)])
    assert(list(zip([1], [2], [3], [4], [5], [6])) == [(1, 2, 3, 4, 5, 6)])
    assert(list(zip([1], [2], [3], [4], [5], [6], [7])) == [(1, 2, 3, 4, 5, 6, 7)])
    assert(list(zip([1], [2], [3], [4], [5], [6], [7], [8])) == [(1, 2, 3, 4, 5, 6, 7, 8)])
    assert(list(zip([1], [2], [3], [4], [5], [6], [7], [8], [9])) == [(1, 2, 3, 4, 5, 6, 7, 8, 9)])
    assert(list(zip([1], [2], [3], [4], [5], [6], [7], [8], [10])) == [(1, 2, 3, 4, 5, 6, 7, 8, 10)])


def test_open_path_like():
    import sys

    if sys.version_info >= (3, 6):
        class A:
            def __fspath__(self):
                return sys.argv[0]

        with open(A()) as f:
            assert f.name == sys.argv[0]


def test_classmethod():
    import abc


    # test __init__
    def a():
        pass


    assert isinstance(classmethod(a), classmethod)


    # test __new__
    def b():
        pass


    def c():
        pass


    def d():
        pass


    def e():
        pass


    assert isinstance(classmethod.__new__(classmethod, b, c, d=d, e=e), classmethod)


    # test __func__
    def f():
        pass


    assert classmethod(f).__func__ == f


    # test __isabstractmethod__
    @abc.abstractmethod
    def g():
        pass


    def h():
        pass


    assert classmethod(g).__isabstractmethod__
    assert not classmethod(h).__isabstractmethod__


    # test __get__
    class WrappedWithSM:
        @classmethod
        def foo(cls):
            return 10


    class ReassignedWithSM:
        def foo(cls):
            return 10
        foo = classmethod(foo)


    assert type(WrappedWithSM.__dict__["foo"].__get__(WrappedWithSM, type)).__name__ == "method"
    assert type(WrappedWithSM.__dict__["foo"].__get__(WrappedWithSM)).__name__ == "method"
    assert type(ReassignedWithSM.__dict__["foo"].__get__(ReassignedWithSM, type)).__name__ == "method"
    assert type(ReassignedWithSM.__dict__["foo"].__get__(ReassignedWithSM)).__name__ == "method"


    # test __dict__.keys()
    assert set(classmethod.__dict__.keys()) == {'__init__', '__new__', '__func__', '__isabstractmethod__', '__get__',
                                                 '__dict__', '__doc__'}


def test_staticmethod():
    import abc


    # test __init__
    def a():
        pass


    assert isinstance(staticmethod(a), staticmethod)


    # test __new__
    def b():
        pass


    def c():
        pass


    def d():
        pass


    def e():
        pass


    assert isinstance(staticmethod.__new__(staticmethod, b, c, d=d, e=e), staticmethod)


    # test __func__
    def f():
        pass


    assert staticmethod(f).__func__ == f


    # test __isabstractmethod__
    @abc.abstractmethod
    def g():
        pass


    def h():
        pass


    assert staticmethod(g).__isabstractmethod__
    assert not staticmethod(h).__isabstractmethod__


    # test __get__
    class WrappedWithSM:
        @staticmethod
        def foo():
            return 10


    class ReassignedWithSM:
        def foo():
            return 10
        foo = staticmethod(foo)


    assert type(WrappedWithSM.__dict__["foo"].__get__(WrappedWithSM, type)).__name__ == "function"
    assert type(WrappedWithSM.__dict__["foo"].__get__(WrappedWithSM)).__name__ == "function"
    assert type(ReassignedWithSM.__dict__["foo"].__get__(ReassignedWithSM, type)).__name__ == "function"
    assert type(ReassignedWithSM.__dict__["foo"].__get__(ReassignedWithSM)).__name__ == "function"


    # test __dict__.keys()
    assert set(staticmethod.__dict__.keys()) == {'__init__', '__new__', '__func__', '__isabstractmethod__', '__get__',
                                                 '__dict__', '__doc__'}


def test_dict_update():
    d = {}
    d.update({"k1": 1, "v1": 1})
    d.update([("k2", 2), ("v2", 2)])
    d.update(k3=3, v3=3)
    assert d == {"k1": 1, "v1": 1, "k2": 2, "v2": 2, "k3": 3, "v3": 3}