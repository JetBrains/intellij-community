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
obj.g(10)
obj.g([])
