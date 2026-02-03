def test():
    abs(False)
    int(10)
    long(False)
    float(False)
    complex(False)
    divmod(False, False)
    divmod(<warning descr="Expected type '_N2 ≤: Union[int, float]', got 'str' instead">'foo'</warning>, <warning descr="Expected type '_N2 ≤: Union[int, float]', got 'unicode' instead">u'bar'</warning>)
    pow(False, True)
    round<warning descr="Unexpected type(s):(bool, str)Possible type(s):(float, int)(SupportsFloat, int)">(False, 'foo')</warning>
