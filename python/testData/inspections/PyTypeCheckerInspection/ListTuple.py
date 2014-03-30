def f(spam, eggs):
    """
    :type spam: list of string
    :type eggs: (bool, int, unicode)
    """
    return spam, eggs


def test():
    f(<warning descr="Expected type 'list[str | unicode]', got 'list[int]' instead">[1, 2, 3]</warning>,
      (<warning descr="Expected type '(bool, int, unicode)', got '(bool, int, str)' instead">False, 2, ''</warning>))
