def test():
    def f1(x):
        """
        :type x: int
        """
        pass

    class C:
        def f(self):
            raise NotImplementedError()

    x = C()
    f1(x.f())
