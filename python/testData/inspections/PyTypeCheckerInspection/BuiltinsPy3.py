def test_operators():
    print(2 + <warning descr="Expected type 'int', got 'str' instead">'foo'</warning>)
    print(b'foo' + <warning descr="Expected type 'bytes', got 'str' instead">'bar'</warning>)
    print(b'foo' + <warning descr="Expected type 'bytes', got 'int' instead">3</warning>)


def test_numerics():
    abs(False)
    int(10)
    long(False)
    float(False)
    complex(False)
    divmod(False, False)
    divmod(<warning descr="Expected type '_N', got 'bytes' instead">b'foo'</warning>, <warning descr="Expected type '_N', got 'str' instead">'bar'</warning>)
    pow(False, True)
    round<warning descr="Unexpected type(s):(bool, str)Possible types:(SupportsRound[int], int)(SupportsRound[int], None)(float, int)(float, None)">(False, 'foo')</warning>
