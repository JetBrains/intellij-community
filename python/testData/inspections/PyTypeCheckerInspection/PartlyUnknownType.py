def test():
    def f():
        """
        :rtype: None or unknown or int or long
        """
    def g(x):
        """
        :type x: object
        """
    g(f())
