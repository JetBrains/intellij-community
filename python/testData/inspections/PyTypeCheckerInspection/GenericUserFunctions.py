def test():
    def f1(xs):
        """
        :type xs: collections.Iterable of T
        """
        return iter(xs).next()

    def f2(x, xs, z):
        """
        :type x: T
        :type xs: list of T
        :type z: U
        """
        return x in xs

    def id(x):
        """
        :type x: T
        :rtype: T
        """
        return x

    def f3(x, y, z):
        """
        :type x: T
        :type y: U
        :type z: V
        """
        r1 = id(x)
        r2 = id(y)
        r3 = id(z)
        return r1, r2, r3

    def f4(x):
        """
        :type x: (bool, int, str)
        """

    result = f1([1, 2, 3])
    print(result)
    print(result + <warning descr="Expected type 'Number', got 'str' instead">'foo'</warning>)

    f2(1, <weak_warning descr="Expected type 'List[int]' (matched generic type 'List[TypeVar('T')]'), got 'List[str]' instead">['foo']</weak_warning>, 'bar')

    result = f3(1, 'foo', True)
    f4(<warning descr="Expected type 'Tuple[bool, int, str]', got 'Tuple[int, str, bool]' instead">result</warning>)
