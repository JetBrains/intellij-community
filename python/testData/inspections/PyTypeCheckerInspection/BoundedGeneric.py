def test():
    def f(x):
        """
        :type x: T <= int or str
        :rtype: T
        """
        pass

    x = f(10)
    y = f('foo')
    z = f(<warning descr="Expected type 'T (one of (int, str))', got 'list' instead">[]</warning>)
    return x + <warning descr="Expected type 'one of (int, long, float, complex)', got 'str' instead">y</warning>
