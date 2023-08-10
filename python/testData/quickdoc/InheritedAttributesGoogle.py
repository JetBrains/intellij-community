class Parent:
    """
    Attributes:
        a1: a1 doc from parent
        a2: a2 doc from parent
    """

    a1 = 0

    def __init__(self):
        self.a2 = 0


class Chi<the_ref>ld(Parent):
    """
    Attributes:
        a1: a1 doc from child
        a2: a2 doc from child
    """
    def __init__(self):
        super().__init__()