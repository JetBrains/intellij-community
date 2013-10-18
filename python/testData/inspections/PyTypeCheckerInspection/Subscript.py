def test():
    def f(x):
        """
        :type x: str
        """
    class C(object):
        def __getitem__(self, item):
            """
            :type item: str
            :rtype: int
            """
    xs = [1, 2, 3]
    x = xs[0]
    f(<warning descr="Expected type 'str', got 'int' instead">x</warning>)
    c = C()
    c_0 = c[<warning descr="Expected type 'str', got 'int' instead">0</warning>]
    f(<warning descr="Expected type 'str', got 'int' instead">c_0</warning>)
