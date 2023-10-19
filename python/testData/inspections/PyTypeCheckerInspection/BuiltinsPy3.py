def test_operators():
    print(2 + <warning descr="Expected type 'int', got 'LiteralString' instead">'foo'</warning>)
    print(b'foo' + <warning descr="Expected type 'Buffer | Buffer', got 'LiteralString' instead">'bar'</warning>)
    print(b'foo' + <warning descr="Expected type 'Buffer | Buffer', got 'int' instead">3</warning>)


def test_numerics():
    abs(False)
    int(10)
    long(False)
    float(False)
    complex(False)
    divmod(False, False)
    divmod<warning descr="Unexpected type(s):(bytes, LiteralString)Possible type(s):(SupportsDivMod, LiteralString)(bytes, SupportsRDivMod[bytes, Any])">(b'foo', 'bar')</warning>
    pow(False, True)
    round<warning descr="Unexpected type(s):(bool, LiteralString)Possible type(s):(_SupportsRound1[int], None)(_SupportsRound2[int], SupportsIndex)">(False, 'foo')</warning>
