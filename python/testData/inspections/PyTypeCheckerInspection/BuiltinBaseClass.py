def test():
    class MyInt(int):
        pass
    def f(x):
        """
        :type x: MyInt
        """
    i = MyInt(2)
    f(i)
