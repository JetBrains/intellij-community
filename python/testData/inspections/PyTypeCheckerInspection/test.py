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


def test_1():
    p7 = int('7')
    f1(1,
       <warning descr="Expected type 'int', got 'str' instead">'2'</warning>,
       3.0, 4, 5, int('6'), p7, p8=-8,
       <warning descr="Expected type 'int', got 'str' instead">p9='foo'</warning>,
       <warning descr="Expected type 'int', got 'str' instead">p10='foo'</warning>)


def str_to_none(b):
    """
    :type b: str
    """
    pass


def unicode_to_none(s):
    """
    :type s: unicode
    """
    pass


def string_to_none(s):
    """
    :type s: string
    """
    pass


def str_or_unicode_to_none(s):
    """
    :type s: str or unicode
    """
    pass


def test_str_unicode():
    b1 = 'hello'
    s1 = u'привет'
    b2 = str(-1)
    s2 = unicode(3.14)
    ENC = 'utf-8'
    str_to_none(<warning descr="Expected type 'str', got 'unicode' instead">b1.decode(ENC)</warning>)
    unicode_to_none(b1.decode(ENC))
    string_to_none(b1.decode(ENC))
    str_or_unicode_to_none(b1.decode(ENC))
    b1.encode(ENC)
    s1.decode(ENC)
    str_to_none(s1.encode(ENC))
    unicode_to_none(<warning descr="Expected type 'unicode', got 'str' instead">s1.encode(ENC)</warning>)
    string_to_none(<warning descr="Expected type 'unicode', got 'str' instead">s1.encode(ENC)</warning>)
    str_or_unicode_to_none(s1.encode(ENC))
    b2.decode(ENC)
    b2.encode(ENC)
    s2.decode(ENC)
    s2.encode(ENC)


def f_list_tuple(spam, eggs):
    """
    :type spam: list of string
    :type eggs: (bool, int, string)
    """
    return spam, eggs


def test_list_tuple():
    f_list_tuple(<warning descr="Expected type 'list of unicode', got 'list of int' instead">[1, 2, 3]</warning>,
                 (<warning descr="Expected type 'tuple(bool,int,unicode)', got 'tuple(bool,int,str)' instead">False, 2, ''</warning>))


def test_builtin_numerics():
    abs(False)
    int(10)
    long(False)
    float(False)
    complex(False)
    divmod(False, False)
    divmod(<warning descr="Expected type 'one of (bool, int, long, float, complex)', got 'str' instead">'foo'</warning>,
           <warning descr="Expected type 'one of (bool, int, long, float, complex)', got 'unicode' instead">u'bar'</warning>)
    pow(False, True)
    round(False,
          <warning descr="Expected type 'one of (bool, int, long, float, None)', got 'str' instead">'foo'</warning>)


def test_generator():
    def gen(n):
        for x in xrange(n):
            yield str(x)
    def f(xs):
        """
        :type xs: list of int
        """
        return xs
    return (''.join(gen(10)),
            f(<warning descr="Expected type 'list of int', got 'list of str' instead">gen(11)</warning>))


