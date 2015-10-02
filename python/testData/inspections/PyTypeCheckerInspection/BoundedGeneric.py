def test():
    def f(x):
        """
        :type x: T <= int | str
        :rtype: T
        """
        pass

    x = f(10)
    y = f('foo')
    z = f(<warning descr="Expected type 'TypeVar('T', int, str)', got 'list' instead">[]</warning>)
    return x + <warning descr="Expected type 'Number', got 'str' instead">y</warning>
