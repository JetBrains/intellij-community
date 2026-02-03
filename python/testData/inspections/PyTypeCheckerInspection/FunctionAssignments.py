def test():
    def g(x):
        """
        :type x: int
        """
        return x
    g(<warning descr="Expected type 'int', got 'str' instead">"str"</warning>) #fail
    h = g
    h(<warning descr="Expected type 'int', got 'str' instead">"str"</warning>) #fail


