class C:
    def g(self, x):
        """
        :type x: int
        """
        pass

    def method_c(self):
        pass


class D:
    def g(self, x):
        """
        :type x: list
        """
        pass

    def method_d(self):
        pass


def f():
    """
    :rtype: C or D
    """
    pass


obj = f()
obj.g(<warning descr="Expected type 'list', got 'Literal[10]' instead">10</warning>)
obj.g(<warning descr="Expected type 'int', got 'List[Unknown]' instead">[]</warning>)
