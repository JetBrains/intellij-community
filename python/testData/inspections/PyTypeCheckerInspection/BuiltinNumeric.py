def test():
    abs(False)
    int(10)
    long(False)
    float(False)
    complex(False)
    divmod(False, False)
    divmod(<warning descr="Expected type '_N2 ≤: Union[int, float]', got 'str' instead">'foo'</warning>, <warning descr="Expected type '_N2 ≤: Union[int, float]', got 'unicode' instead">u'bar'</warning>)
    pow(False, True)
    round<warning descr="No overload of 'round' matches the arguments. Argument types: (bool, str). Expected one of: (number: Union[float, int], ndigits: int), (number: SupportsFloat, ndigits: int)">(False, 'foo')</warning>
