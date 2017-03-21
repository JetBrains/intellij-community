def test():
    abs(False)
    int(10)
    long(False)
    float(False)
    complex(False)
    divmod(False, False)
    divmod<warning descr="Unexpected type(s):(str, unicode)Possible types:(float, float)(int, int)">('foo', u'bar')</warning>
    pow(False, True)
    round<warning descr="Unexpected type(s):(bool, str)Possible types:(SupportsRound, int)(float, int)">(False, 'foo')</warning>
