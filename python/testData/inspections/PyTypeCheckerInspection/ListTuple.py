def f(spam, eggs):
    """
    :type spam: list of string
    :type eggs: (bool, int, dict)
    """
    return spam, eggs


def test():
    f(<warning descr="Expected type 'List[Union[str, unicode]]', got 'List[Literal[1, 2, 3]]' instead">[1, 2, 3]</warning>,
      <warning descr="Expected type 'Tuple[bool, int, dict]', got 'Tuple[bool, Literal[2], str]' instead">(False, 2, '')</warning>)
