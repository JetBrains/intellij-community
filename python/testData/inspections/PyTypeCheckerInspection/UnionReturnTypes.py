def test(c):
    def f1(c):
        if c < 0:
            return []
        elif c > 0:
            return 'foo'
        else:
            return None
    def f2(x):
        """
        :type x: str
        """
        pass
    def f3(x):
        """
        :type x: int
        """
    x1 = f1(c)
    f2(x1)  # Weaker union types
    f3(<warning descr="Expected type 'int', got 'Union[list, str, None]' instead">x1</warning>)

    f2(<warning descr="Expected type 'str', got 'int' instead">x1.count('')</warning>)
    f3(x1.count(''))
    f2(x1.strip())
    f3(<warning descr="Expected type 'int', got 'str' instead">x1.strip()</warning>)
