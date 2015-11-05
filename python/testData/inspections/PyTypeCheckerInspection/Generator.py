def test():
    def gen(n):
        for x in xrange(n):
            yield str(x)
    def f_1(xs):
        """
        :type xs: list of int
        """
        return xs
    def f_2(xs):
        """
        :type xs: collections.Sequence of int
        """
        return xs
    def f_3(xs):
        """
        :type xs: collections.Container of int
        """
        return xs
    def f_4(xs):
        """
        :type xs: collections.Iterator of int
        """
        return xs
    def f_5(xs):
        """
        :type xs: collections.Iterable of int
        """
        return xs
    def f_6(xs):
        """
        :type xs: list
        """
        return xs
    def f_7(xs):
        """
        :type xs: collections.Sequence
        """
        return xs
    def f_8(xs):
        """
        :type xs: collections.Container
        """
        return xs
    def f_9(xs):
        """
        :type xs: collections.Iterator
        """
        return xs
    def f_10(xs):
        """
        :type xs: collections.Iterable
        """
        return xs
    def f_11(xs):
        """
        :type xs: list of string
        """
        return xs
    def f_12(xs):
        """
        :type xs: collections.Sequence of string
        """
        return xs
    def f_13(xs):
        """
        :type xs: collections.Container of string
        """
        return xs
    def f_14(xs):
        """
        :type xs: collections.Iterator of string
        """
        return xs
    def f_15(xs):
        """
        :type xs: collections.Iterable of string
        """
        return xs
    return [
        ''.join(gen(10)),
        f_1(<warning descr="Expected type 'List[int]', got '__generator[str, Any, None]' instead">gen(11)</warning>),
        f_2(<warning descr="Expected type 'Sequence[int]', got '__generator[str, Any, None]' instead">gen(11)</warning>),
        f_3(<warning descr="Expected type 'Container[int]', got '__generator[str, Any, None]' instead">gen(11)</warning>),
        f_4(<warning descr="Expected type 'Iterator[int]', got '__generator[str, Any, None]' instead">gen(11)</warning>),
        f_5(<warning descr="Expected type 'Iterable[int]', got '__generator[str, Any, None]' instead">gen(11)</warning>),
        f_6(<warning descr="Expected type 'list', got '__generator[str, Any, None]' instead">gen(11)</warning>),
        f_7(<warning descr="Expected type 'Sequence', got '__generator[str, Any, None]' instead">gen(11)</warning>),
        f_8(<warning descr="Expected type 'Container', got '__generator[str, Any, None]' instead">gen(11)</warning>),
        f_9(gen(11)),
        f_10(gen(11)),
        f_11(<warning descr="Expected type 'List[Union[str, unicode]]', got '__generator[str, Any, None]' instead">gen(11)</warning>),
        f_12(<warning descr="Expected type 'Sequence[Union[str, unicode]]', got '__generator[str, Any, None]' instead">gen(11)</warning>),
        f_13(<warning descr="Expected type 'Container[Union[str, unicode]]', got '__generator[str, Any, None]' instead">gen(11)</warning>),
        f_14(gen(11)),
        f_15(gen(11)),
        f_15('foo'.split('o')),
    ]
