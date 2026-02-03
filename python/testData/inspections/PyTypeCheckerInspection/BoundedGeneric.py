def test():
    def f(x):
        """
        :type x: T <= int | str
        :rtype: T
        """
        pass

    x = f(10)
    y = f('foo')
    z = f(<warning descr="Expected type 'T ≤: Union[int, str]', got 'List[Any]' instead">[]</warning>)
    return x + <warning descr="Expected type 'int', got 'str' instead">y</warning>
