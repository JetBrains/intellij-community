def test_right_operators():
    o = object()
    xs = [
        <warning descr="Expected type 'one of (int, long)', got 'object' instead">o</warning> * [],
    ]
