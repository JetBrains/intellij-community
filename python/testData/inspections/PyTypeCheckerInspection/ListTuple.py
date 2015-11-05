def f(spam, eggs):
    """
    :type spam: list of string
    :type eggs: (bool, int, unicode)
    """
    return spam, eggs


def test():
    f(<warning descr="Expected type 'List[Union[str, unicode]]', got 'List[int]' instead">[1, 2, 3]</warning>,
      <warning descr="Expected type 'Tuple[bool, int, unicode]', got 'Tuple[bool, int, str]' instead">(False, 2, '')</warning>)
