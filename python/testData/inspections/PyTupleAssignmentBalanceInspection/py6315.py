def test_tuple_slice():
    def f():
        return 1, 2, 3
    x, y = f()[:2]