def test():
    def f(x):
        """
        :type x: str
        """
        pass
    xs = [1.1, 2.2, 3.3]
    for i, x in enumerate(xs):
        f(<warning descr="Expected type 'str', got 'int' instead">i</warning>)
        f(<warning descr="Expected type 'str', got 'float' instead">x</warning>)


