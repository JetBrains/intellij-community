def test():
    def f(x):
        """
        :type x: str
        """
        pass
    class C(object):
        def __gt__(self, other):
            return []
    o = object()
    c = C()
    f(<warning descr="Expected type 'str', got 'bool' instead">1 < 2</warning>)
    f(<warning descr="Expected type 'str', got 'bool' instead">o == o</warning>)
    f(<warning descr="Expected type 'str', got 'bool' instead">o >= o</warning>)
    f(<warning descr="Expected type 'str', got 'bool' instead">'foo' > o</warning>)
    f(<warning descr="Expected type 'str', got 'bool' instead">c < 1</warning>)
    f(<warning descr="Expected type 'str', got 'list' instead">c > 1</warning>)
    f(<warning descr="Expected type 'str', got 'bool' instead">c == 1</warning>)
    f(<warning descr="Expected type 'str', got 'bool' instead">c in [1, 2, 3]</warning>)
