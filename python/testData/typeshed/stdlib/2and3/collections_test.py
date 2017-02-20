def test_namedtuple():
    from collections import namedtuple

    Point = namedtuple('Point', 'x y')
    p = Point(1, 2)

    assert p == Point(1, 2)
    assert p == (1, 2)
    assert p._replace(y=3.14).y == 3.14
    assert p._asdict()['x'] == 1
    assert (p.x, p.y) == (1, 2)
    assert p[0] + p[1] == 3
    assert p.index(1) == 0
    assert Point._make([1, 3.14]).y == 3.14
