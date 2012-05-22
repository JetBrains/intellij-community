def test():
    abs(False)
    int(10)
    long(False)
    float(False)
    complex(False)
    divmod(False, False)
    divmod(<warning descr="Expected type 'one of (int, long, float, complex)', got 'str' instead">'foo'</warning>,
    <warning descr="Expected type 'one of (int, long, float, complex)', got 'unicode' instead">u'bar'</warning>)
    pow(False, True)
    round(False,
    <warning descr="Expected type 'one of (int, long, float, None)', got 'str' instead">'foo'</warning>)
