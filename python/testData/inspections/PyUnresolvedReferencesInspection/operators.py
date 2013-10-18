class C(object):
    def __add__(self, other):
        return int(other)

    def __or__(self, other):
        return other

    def __rsub__(self, other):
        return other

    def __neg__(self):
        return self


def test_object():
    o1 = object()
    o2 = object()
    o1 <warning descr="Class 'object' does not define '__add__', so the '+' operator cannot be used on its instances">+</warning> o2 #fail
    o1 <warning descr="Class 'object' does not define '__sub__', so the '-' operator cannot be used on its instances">-</warning> o2 #fail
    o1 <warning descr="Class 'object' does not define '__mul__', so the '*' operator cannot be used on its instances">*</warning> o2 #fail
    o1 <warning descr="Class 'object' does not define '__div__', so the '/' operator cannot be used on its instances">/</warning> o2 #fail
    o1 <warning descr="Class 'object' does not define '__mod__', so the '%' operator cannot be used on its instances">%</warning> o2 #fail
    o1 <warning descr="Class 'object' does not define '__pow__', so the '**' operator cannot be used on its instances">**</warning> o2 #fail
    o1 <warning descr="Class 'object' does not define '__lshift__', so the '<<' operator cannot be used on its instances"><<</warning> o2 #fail
    o1 <warning descr="Class 'object' does not define '__rshift__', so the '>>' operator cannot be used on its instances">>></warning> o2 #fail
    o1 <warning descr="Class 'object' does not define '__and__', so the '&' operator cannot be used on its instances">&</warning> o2 #fail
    o1 <warning descr="Class 'object' does not define '__or__', so the '|' operator cannot be used on its instances">|</warning> o2 #fail
    o1 <warning descr="Class 'object' does not define '__xor__', so the '^' operator cannot be used on its instances">^</warning> o2 #fail
    o1 <warning descr="Class 'object' does not define '__floordiv__', so the '//' operator cannot be used on its instances">//</warning> o2 #fail
    o2 < o1 < o2, o2 <= o1 <= o2, o1 == o2, o1 != o2, o2 in o1 #pass


def test_custom_class():
    c = C()
    o = object()
    c + o, c | o, o - c #pass
    c <warning descr="Class 'C' does not define '__sub__', so the '-' operator cannot be used on its instances">-</warning> o #fail
    c <warning descr="Class 'C' does not define '__mul__', so the '*' operator cannot be used on its instances">*</warning> o #fail
    c <warning descr="Class 'C' does not define '__div__', so the '/' operator cannot be used on its instances">/</warning> o #fail
    c <warning descr="Class 'C' does not define '__mod__', so the '%' operator cannot be used on its instances">%</warning> o #fail
    c <warning descr="Class 'C' does not define '__pow__', so the '**' operator cannot be used on its instances">**</warning> o #fail
    c <warning descr="Class 'C' does not define '__lshift__', so the '<<' operator cannot be used on its instances"><<</warning> o #fail
    c <warning descr="Class 'C' does not define '__rshift__', so the '>>' operator cannot be used on its instances">>></warning> o #fail
    c <warning descr="Class 'C' does not define '__and__', so the '&' operator cannot be used on its instances">&</warning> o #fail
    c <warning descr="Class 'C' does not define '__xor__', so the '^' operator cannot be used on its instances">^</warning> o #fail
    c <warning descr="Class 'C' does not define '__floordiv__', so the '//' operator cannot be used on its instances">//</warning> o #fail
    o < c < o, o <= c <= o, c == o, c != o, o in c #pass


def test_builtins():
    i = 0
    o = object()
    i + o, i - o, i * o, i / o, i % o, i ** o, i << o, i >> o, i & o, i | o, i ^ o, i // o #pass
    o < i < o, o <= i <= o, i == o, i != o, o in i #pass
    o + i, o - i, o * i, o / i, o % i, o ** i, o << i, o >> i, o & i, o | i, o ^ i, o // i #pass

    s = 'foo'
    s + o, s * o, s % o #pass
    s <warning descr="Class 'str' does not define '__sub__', so the '-' operator cannot be used on its instances">-</warning> o #fail
    s <warning descr="Class 'str' does not define '__div__', so the '/' operator cannot be used on its instances">/</warning> o #fail
    s <warning descr="Class 'str' does not define '__pow__', so the '**' operator cannot be used on its instances">**</warning> o #fail
    s <warning descr="Class 'str' does not define '__lshift__', so the '<<' operator cannot be used on its instances"><<</warning> o #fail
    s <warning descr="Class 'str' does not define '__rshift__', so the '>>' operator cannot be used on its instances">>></warning> o #fail
    s <warning descr="Class 'str' does not define '__and__', so the '&' operator cannot be used on its instances">&</warning> o #fail
    s <warning descr="Class 'str' does not define '__or__', so the '|' operator cannot be used on its instances">|</warning> o #fail
    s <warning descr="Class 'str' does not define '__xor__', so the '^' operator cannot be used on its instances">^</warning> o #fail
    s <warning descr="Class 'str' does not define '__floordiv__', so the '//' operator cannot be used on its instances">//</warning> o #fail
    o < s < o, o <= s <= o, s == o, s != o, o in s #pass

    xs = []
    xs + o, xs * o #pass
    xs <warning descr="Class 'list' does not define '__sub__', so the '-' operator cannot be used on its instances">-</warning> o #fail
    xs <warning descr="Class 'list' does not define '__div__', so the '/' operator cannot be used on its instances">/</warning> o #fail
    xs <warning descr="Class 'list' does not define '__mod__', so the '%' operator cannot be used on its instances">%</warning> o #fail
    xs <warning descr="Class 'list' does not define '__pow__', so the '**' operator cannot be used on its instances">**</warning> o #fail
    xs <warning descr="Class 'list' does not define '__lshift__', so the '<<' operator cannot be used on its instances"><<</warning> o #fail
    xs <warning descr="Class 'list' does not define '__rshift__', so the '>>' operator cannot be used on its instances">>></warning> o #fail
    xs <warning descr="Class 'list' does not define '__and__', so the '&' operator cannot be used on its instances">&</warning> o #fail
    xs <warning descr="Class 'list' does not define '__or__', so the '|' operator cannot be used on its instances">|</warning> o #fail
    xs <warning descr="Class 'list' does not define '__xor__', so the '^' operator cannot be used on its instances">^</warning> o #fail
    xs <warning descr="Class 'list' does not define '__floordiv__', so the '//' operator cannot be used on its instances">//</warning> o #fail
    o < xs < o, o <= xs <= o, xs == o, xs != o, o in xs #pass


def test_subscription():
    class C(object):
        def __getitem__(self, key, value):
            pass

        def __setitem__(self, item):
            pass

        def __delitem__(self, item):
            pass

    class D(object):
        pass

    class E(object):
        def __getitem__(self, item):
            pass

    c = C()
    c[0] = 0
    print(c[0])
    del c[0]

    d = D()
    d<warning descr="Class 'D' does not define '__setitem__', so the '[]' operator cannot be used on its instances">[</warning>0] = 0
    print(d<warning descr="Class 'D' does not define '__getitem__', so the '[]' operator cannot be used on its instances">[</warning>0])
    del d<warning descr="Class 'D' does not define '__delitem__', so the '[]' operator cannot be used on its instances">[</warning>0]

    e = E()
    e<warning descr="Class 'E' does not define '__setitem__', so the '[]' operator cannot be used on its instances">[</warning>0] = 0
    print(e[0])
    del e<warning descr="Class 'E' does not define '__delitem__', so the '[]' operator cannot be used on its instances">[</warning>0]


def test_unary_operators():
    o = object()
    print(<warning descr="Class 'object' does not define '__pos__', so the '+' operator cannot be used on its instances">+</warning>o)
    print(<warning descr="Class 'object' does not define '__neg__', so the '-' operator cannot be used on its instances">-</warning>o)
    print(<warning descr="Class 'object' does not define '__invert__', so the '~' operator cannot be used on its instances">~</warning>o)

    i = 1
    print(+i)
    print(-i)
    print(~i)

    c = C()
    print(<warning descr="Class 'C' does not define '__pos__', so the '+' operator cannot be used on its instances">+</warning>c)
    print(-c)
    print(<warning descr="Class 'C' does not define '__invert__', so the '~' operator cannot be used on its instances">~</warning>c)