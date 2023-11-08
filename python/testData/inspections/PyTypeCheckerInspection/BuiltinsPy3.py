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
    divmod<warning descr="Unexpected type(s):(bytes, str)Possible type(s):(SupportsDivMod[_T_contra, _T_co], str)(bytes, SupportsRDivMod[bytes, _T_co])">(b'foo', 'bar')</warning>
    pow(False, True)
    round<warning descr="Unexpected type(s):(bool, str)Possible type(s):(SupportsRound, None)(SupportsRound[int], SupportsIndex)">(False, 'foo')</warning>
