def f(x, <weak_warning descr="Parameter 'y' value is not used">y</weak_warning>):
    """
    >>> print(x)
    """


def bar():
    def tokenize():
        """
        >>> tokenize()
        ['foo', Escaped('='), 'bar', Escaped('\\'), 'baz']
 
        """