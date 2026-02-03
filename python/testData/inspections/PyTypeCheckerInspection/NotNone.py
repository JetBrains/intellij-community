def test():
    def f(x):
        """
        :type x: int or str or list
        """
    def f1():
        """
        :rtype: int or None
        """
    def f2():
        """
        :rtype: int or str or None
        """
    x1 = f1()
    x2 = f2()
    x3 = 1
    f(<warning descr="Expected type 'Union[int, str, list]', got 'Optional[int]' instead">x1</warning>)
    f(<warning descr="Expected type 'Union[int, str, list]', got 'Union[int, str, None]' instead">x2</warning>)
    f(x3)
    if x1:
        f(x1)
    if x2:
        f(x2)
    if x3:
        f(x3)
    if x1 is not None:
        f(x1)
    elif x2 is not None:
        f(x2)
    elif x3 is not None:
        f(x3)
