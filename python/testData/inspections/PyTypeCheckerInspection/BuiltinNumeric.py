def test():
    abs(False)
    int(10)
    long(False)
    float(False)
    complex(False)
    divmod(False, False)
    divmod(<warning descr="Expected type '_N2', got 'str' instead">'foo'</warning>, <warning descr="Expected type '_N2', got 'unicode' instead">u'bar'</warning>)
    pow(False, True)
    round<warning descr="Unexpected type(s):(bool, str)Possible type(s):(SupportsFloat, int)(float, int)">(False, 'foo')</warning>
