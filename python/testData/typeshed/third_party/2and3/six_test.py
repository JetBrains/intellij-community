def test_python_checks():
    from six import PY2, PY3

    assert PY2 ^ PY3


def test_xrange():
    from six.moves import xrange

    xs = xrange(5)
    assert xs.__iter__
    assert xs[0] == 0
    assert sum(xs) == 10
