def test_operators():
    print(2 + <warning descr="Expected type 'int', got 'str' instead">'foo'</warning>)
    print(b'foo' + <warning descr="Expected type 'Buffer', got 'str' instead">'bar'</warning>)
    print(b'foo' + <warning descr="Expected type 'Buffer', got 'int' instead">3</warning>)


def test_numerics():
    abs(False)
    int(10)
    long(False)
    float(False)
    complex(False)
    divmod(False, False)
    divmod<warning descr="No overload of 'divmod' matches the arguments. Argument types: (bytes, str). Expected one of: (x: SupportsDivMod[_T_contra, _T_co], y: str), (x: bytes, y: SupportsRDivMod[bytes, _T_co])">(b'foo', 'bar')</warning>
    pow(False, True)
    round<warning descr="No overload of 'round' matches the arguments. Argument types: (bool, str). Expected one of: (number: _SupportsRound1[int], ndigits: None), (number: _SupportsRound2[int], ndigits: SupportsIndex)">(False, 'foo')</warning>
