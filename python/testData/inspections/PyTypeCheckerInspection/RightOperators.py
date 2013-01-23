class C(object):
    pass


def test_right_operators():
    o = C()
    xs = [
        <warning descr="Expected type 'one of (int, long)', got 'C' instead">o</warning> * [],
    ]
