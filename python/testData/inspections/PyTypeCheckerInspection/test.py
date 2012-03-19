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
    string_to_none(s1.encode(ENC))
    str_or_unicode_to_none(s1.encode(ENC))
    b2.decode(ENC)
    b2.encode(ENC)
    s2.decode(ENC)
    s2.encode(ENC)


def f_list_tuple(spam, eggs):
    """
    :type spam: list of string
    :type eggs: (bool, int, unicode)
    """
    return spam, eggs


def test_list_tuple():
    f_list_tuple(<warning descr="Expected type 'list of one of (str, unicode)', got 'list of int' instead">[1, 2, 3]</warning>,
                 (<warning descr="Expected type '(bool,int,unicode)', got '(bool,int,str)' instead">False, 2, ''</warning>))


def test_builtin_numerics():
    abs(False)
    int(10)
    long(False)
    float(False)
    complex(False)
    divmod(False, False)
    divmod(<warning descr="Expected type 'one of (int, long, float, complex)', got 'str' instead">'foo'</warning>,
           <warning descr="Expected type 'one of (int, long, float, complex)', got 'unicode' instead">u'bar'</warning>)
    pow(False, True)
    round(False,
          <warning descr="Expected type 'one of (int, long, float, None)', got 'str' instead">'foo'</warning>)


def test_generator():
    def gen(n):
        for x in xrange(n):
            yield str(x)
    def f_1(xs):
        """
        :type xs: list of int
        """
        return xs
    def f_2(xs):
        """
        :type xs: Sequence of int
        """
        return xs
    def f_3(xs):
        """
        :type xs: Container of int
        """
        return xs
    def f_4(xs):
        """
        :type xs: Iterator of int
        """
        return xs
    def f_5(xs):
        """
        :type xs: Iterable of int
        """
        return xs
    def f_6(xs):
        """
        :type xs: list
        """
        return xs
    def f_7(xs):
        """
        :type xs: Sequence
        """
        return xs
    def f_8(xs):
        """
        :type xs: Container
        """
        return xs
    def f_9(xs):
        """
        :type xs: Iterator
        """
        return xs
    def f_10(xs):
        """
        :type xs: Iterable
        """
        return xs
    def f_11(xs):
        """
        :type xs: list of string
        """
        return xs
    def f_12(xs):
        """
        :type xs: Sequence of string
        """
        return xs
    def f_13(xs):
        """
        :type xs: Container of string
        """
        return xs
    def f_14(xs):
        """
        :type xs: Iterator of string
        """
        return xs
    def f_15(xs):
        """
        :type xs: Iterable of string
        """
        return xs
    return [
        ''.join(gen(10)),
        f_1(<warning descr="Expected type 'list of int', got 'Iterator of str' instead">gen(11)</warning>),
        f_2(<warning descr="Expected type 'Sequence of int', got 'Iterator of str' instead">gen(11)</warning>),
        f_3(<warning descr="Expected type 'Container of int', got 'Iterator of str' instead">gen(11)</warning>),
        f_4(<warning descr="Expected type 'Iterator of int', got 'Iterator of str' instead">gen(11)</warning>),
        f_5(<warning descr="Expected type 'Iterable of int', got 'Iterator of str' instead">gen(11)</warning>),
        f_6(<warning descr="Expected type 'list', got 'Iterator of str' instead">gen(11)</warning>),
        f_7(<warning descr="Expected type 'Sequence', got 'Iterator of str' instead">gen(11)</warning>),
        f_8(<warning descr="Expected type 'Container', got 'Iterator of str' instead">gen(11)</warning>),
        f_9(gen(11)),
        f_10(gen(11)),
        f_11(<warning descr="Expected type 'list of one of (str, unicode)', got 'Iterator of str' instead">gen(11)</warning>),
        f_12(<warning descr="Expected type 'Sequence of one of (str, unicode)', got 'Iterator of str' instead">gen(11)</warning>),
        f_13(<warning descr="Expected type 'Container of one of (str, unicode)', got 'Iterator of str' instead">gen(11)</warning>),
        f_14(gen(11)),
        f_15(gen(11)),
        f_15('foo'.split('o')),
    ]


# PY-4025
def test_function_assignments():
    def g(x):
        """
        :type x: int
        """
        return x
    g(<warning descr="Expected type 'int', got 'str' instead">"str"</warning>) #fail
    h = g
    h(<warning descr="Expected type 'int', got 'str' instead">"str"</warning>) #fail


def test_old_style_classes():
    class C:
        pass
    def f(x):
        """
        :type x: object
        """
        pass
    f(C()) #pass


def test_partly_unknown_type():
    def f():
        """
        :rtype: None or unknown or int or long
        """
    def g(x):
        """
        :type x: object
        """
    g(f())


def test_type_assertions():
    def f_1():
        """
        :rtype: int or str or None
        """
    def f_2():
        """
        :rtype: int or None
        """
    def f_3():
        """
        :rtype: unknown
        """
    def f_4():
        """
        :rtype: object
        """
    def f_5():
        """
        :rtype: int or object
        """
    def f_6():
        """
        :rtype: int or unknown or float
        """
    def f_7():
        """
        :rtype: int or unknown
        """
    def print_int(x):
        """
        :type x: int
        """
        print(x)
    def print_int_or_str(x):
        """
        :type x: int or str
        """
    x_1 = f_1()
    print_int(<warning descr="Expected type 'int', got 'one of (int, str, None)' instead">x_1</warning>)
    print_int_or_str(<warning descr="Expected type 'one of (int, str)', got 'one of (int, str, None)' instead">x_1</warning>)
    if isinstance(x_1, int):
        print_int(x_1)
    if isinstance(x_1, str):
        print_int_or_str(x_1)
    x_7 = f_7()
    print_int(x_7)


def test_local_type_resolve():
    class C():
        def f(self):
            return 2
    c = C()
    x = c.f()
    y = x
    return y + <warning descr="Expected type 'one of (int, long, float, complex)', got 'str' instead">'foo'</warning>


def test_subscription():
    def f(x):
        """
        :type x: str
        """
    class C(object):
        def __getitem__(self, item):
            """
            :type item: str
            :rtype: int
            """
    xs = [1, 2, 3]
    x = xs[0]
    f(<warning descr="Expected type 'str', got 'int' instead">x</warning>)
    c = C()
    c_0 = c[<warning descr="Expected type 'str', got 'int' instead">0</warning>]
    f(<warning descr="Expected type 'str', got 'int' instead">c_0</warning>)


def test_comparison_operators():
    def f(x):
        """
        :type x: str
        """
        pass
    class C(object):
        def __gt__(self, other):
            return []
    o = object()
    c = C()
    f(<warning descr="Expected type 'str', got 'bool' instead">1 < 2</warning>)
    f(<warning descr="Expected type 'str', got 'bool' instead">o == o</warning>)
    f(<warning descr="Expected type 'str', got 'bool' instead">o >= o</warning>)
    f(<warning descr="Expected type 'str', got 'bool' instead">'foo' > o</warning>)
    f(<warning descr="Expected type 'str', got 'bool' instead">c < 1</warning>)
    f(<warning descr="Expected type 'str', got 'list' instead">c > 1</warning>)
    f(<warning descr="Expected type 'str', got 'bool' instead">c == 1</warning>)
    f(<warning descr="Expected type 'str', got 'bool' instead">c in [1, 2, 3]</warning>)


def test_right_operators():
    o = object()
    xs = [
        <warning descr="Expected type 'one of (int, long)', got 'object' instead">o</warning> * [],
    ]


def test_string_integer():
    print('foo' + 'bar')
    print(2 + 3)
    print('foo' + <warning descr="Expected type 'one of (str, unicode)', got 'int' instead">3</warning>)
    print(3 + <warning descr="Expected type 'one of (int, long, float, complex)', got 'str' instead">'foo'</warning>)
    print('foo' + 'bar' * 3)
    print('foo' + 3 * 'bar')
    print('foo' + <warning descr="Expected type 'one of (str, unicode)', got 'int' instead">2 * 3</warning>)


def test_isinstance_implicit_self_types():
    x = 1
    if isinstance(x, unicode):
        x.encode('UTF-8') #pass


def test_not_none():
    def test(x):
        """
        :type x: int or str or list
        """
    def f1():
        """
        :rtype: int or None
        """
    def f2():
        """
        :rtype: int or str or None
        """
    x1 = f1()
    x2 = f2()
    x3 = 1
    test(<warning descr="Expected type 'one of (int, str, list)', got 'one of (int, None)' instead">x1</warning>)
    test(<warning descr="Expected type 'one of (int, str, list)', got 'one of (int, str, None)' instead">x2</warning>)
    test(x3)
    if x1:
        test(x1)
    if x2:
        test(x2)
    if x3:
        test(x3)
    if x1 is not None:
        test(x1)
    elif x2 is not None:
        test(x2)
    elif x3 is not None:
        test(x3)


def test_builtin_functions():
    print(map(str, [1, 2, 3]) + ['foo']) #pass
    print(map(lambda x: x.upper(), 'foo')) #pass
    print(filter(lambda x: x % 2 == 0, [1, 2, 3]) + [4, 5, 6]) #pass
    print(filter(lambda x: x != 'f', 'foo') + 'bar') #pass


def test_union_return_types():
    def f1(c):
        if c < 0:
            return []
        elif c > 0:
            return 'foo'
        else:
            return None
    def f2(x):
        """
        :type x: str
        """
        pass
    def f3(x):
        """
        :type x: int
        """
    x1 = f1(42)
    f2(<warning descr="Expected type 'str', got 'one of (list, str, None)' instead">x1</warning>)
    f3(<warning descr="Expected type 'int', got 'one of (list, str, None)' instead">x1</warning>)

    f2(<warning descr="Expected type 'str', got 'int' instead">x1.count('')</warning>)
    f3(x1.count(''))
    f2(x1.strip())
    f3(<warning descr="Expected type 'int', got 'str' instead">x1.strip()</warning>)


def test_enumerate_iterator():
    def f(x):
        """
        :type x: str
        """
        pass
    xs = [1.1, 2.2, 3.3]
    for i, x in enumerate(xs):
        f(<warning descr="Expected type 'str', got 'int' instead">i</warning>)
        f(<warning descr="Expected type 'str', got 'float' instead">x</warning>)


def test_generic_user_class():
    class User1(object):
        def __init__(self, x):
            """
            :type x: T
            :rtype: User1 of T
            """
            self.x = x

        def get(self):
            """
            :rtype: T
            """
            return self.x

        def put(self, value):
            """
            :type value: T
            """
            self.x = value

    c = User1(10)
    print(c.get() + <warning descr="Expected type 'one of (int, long, float, complex)', got 'str' instead">'foo'</warning>)
    c.put(14)
    c.put(<weak_warning descr="Expected type 'int' (matched generic type 'T'), got 'str' instead">'foo'</weak_warning>)


def test_generic_user_functions():
    def f1(xs):
        """
        :type xs: collections.Iterable of T
        """
        return iter(xs).next()

    def f2(x, xs, z):
        """
        :type x: T
        :type xs: list of T
        :type z: U
        """
        return x in xs

    def id(x):
        """
        :type x: T
        :rtype: T
        """
        return x

    def f3(x, y, z):
        """
        :type x: T
        :type y: U
        :type z: V
        """
        r1 = id(x)
        r2 = id(y)
        r3 = id(z)
        return r1, r2, r3

    def f4(x):
        """
        :type x: (bool, int, str)
        """

    result = f1([1, 2, 3])
    print(result)
    print(result + <warning descr="Expected type 'one of (int, long, float, complex)', got 'str' instead">'foo'</warning>)

    f2(1, <weak_warning descr="Expected type 'list of int' (matched generic type 'list of T'), got 'list of str' instead">['foo']</weak_warning>, 'bar')

    result = f3(1, 'foo', True)
    f4(<warning descr="Expected type '(bool,int,str)', got '(int,str,bool)' instead">result</warning>)


def test_dict_generics(d):
    """
    :type d: dict from int to unicode
    """
    xs = d.items()
    d2 = dict(xs)
    for k, v in d2.items():
        print k + <warning descr="Expected type 'one of (int, long, float, complex)', got 'unicode' instead">v</warning>


# PY-5474
def test_bad_subsription_expr():
    x = r"""\x""
    r"""[<error descr="']' expected">\</error><error descr="Statement expected, found Py:BACKSLASH">t</error><error descr="End of statement expected">\</error><error descr="Statement expected, found Py:BACKSLASH">r</error><error descr="End of statement expected">\</error><error descr="Statement expected, found Py:BACKSLASH">v</error><error descr="End of statement expected">]</error><error descr="Statement expected, found Py:RBRACKET">"</error>""
    """


# PY-5873
def test_type_of_raise_exception():
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
