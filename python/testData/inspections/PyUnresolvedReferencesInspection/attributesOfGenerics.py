class B(object):
    def __init__(self, x):
        """
        :type x: T
        :rtype: B of T
        """
        self._x = x

    def foo(self):
        """
        :rtype: T
        """
        return self._x

class C(B):
    def bar(self):
        expr = self.foo()
        return expr.upper() #pass