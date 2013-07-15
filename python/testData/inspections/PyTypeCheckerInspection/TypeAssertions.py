def test():
    def f_1():
        """
        :rtype: int or str or None
        """
    def f_2():
        """
        :rtype: int or None
        """
    def f_3():
        """
        :rtype: unknown
        """
    def f_4():
        """
        :rtype: object
        """
    def f_5():
        """
        :rtype: int or object
        """
    def f_6():
        """
        :rtype: int or unknown or float
        """
    def f_7():
        """
        :rtype: int or unknown
        """
    def print_int(x):
        """
        :type x: int
        """
        print(x)
    def print_int_or_str(x):
        """
        :type x: int or str
        """
    x_1 = f_1()
    print_int(x_1)  # Weaker union types
    print_int_or_str(x_1)  # Weaker union types
    if isinstance(x_1, int):
        print_int(x_1)
    if isinstance(x_1, str):
        print_int_or_str(x_1)
    x_7 = f_7()
    print_int(x_7)
