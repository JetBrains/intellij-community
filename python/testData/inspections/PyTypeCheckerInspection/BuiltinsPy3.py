def test_operators():
    print(2 + <warning descr="Expected type 'int', got 'str' instead">'foo'</warning>)
    print(b'foo' + <warning descr="Expected type 'Buffer | Buffer', got 'str' instead">'bar'</warning>)
    print(b'foo' + <warning descr="Expected type 'Buffer | Buffer', got 'int' instead">3</warning>)


def test_numerics():
    abs(False)
    int(10)
    long(False)
    float(False)
    complex(False)
    divmod(False, False)
    divmod<warning descr="Unexpected type(s):(bytes, str)Possible type(s):(SupportsDivMod[_T_contra, _T_co], str)(bytes, SupportsRDivMod[bytes, _T_co])">(b'foo', 'bar')</warning>
    pow(False, True)
    round<warning descr="Unexpected type(s):(bool, str)Possible type(s):(_SupportsRound1[int], None)(_SupportsRound2[int], SupportsIndex)">(False, 'foo')</warning>
