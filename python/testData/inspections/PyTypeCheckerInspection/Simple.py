def f1(p1, p2, p3, p4, p5, p6, p7, p8, p9, p10=10, p11='11'):
    """
    :type p1: integer
    :type p2: integer
    :type p3: float
    :type p4: float
    :type p5: int
    :type p6: integer
    :type p7: integer
    :type p8: int
    :type p9: int
    :type p10: int
    :type p11: string
    """
    return p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + p10 + int(p11)


def test():
    p7 = int('7')
    f1(1,
       <warning descr="Expected type 'int', got 'str' instead">'2'</warning>,
       3.0, 4, 5, int('6'), p7, p8=-8,
       <warning descr="Expected type 'int', got 'str' instead">p9='foo'</warning>,
       <warning descr="Expected type 'int', got 'str' instead">p10='foo'</warning>)
