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