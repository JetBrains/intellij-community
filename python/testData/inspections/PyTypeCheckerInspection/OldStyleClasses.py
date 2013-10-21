def test_old_style_classes():
    class C:
        pass
    def f(x):
        """
        :type x: object
        """
        pass
    f(C()) #pass
