class C(object):
    def __add__(self, other):
        return int(other)

    def __or__(self, other):
        return other

    def __rsub__(self, other):
        return other

def test_object():
    o1 = object()
    o2 = object()
    o1 <warning descr="Unresolved attribute reference '+' for class 'object'">+</warning> o2 #fail
    o1 <warning descr="Unresolved attribute reference '-' for class 'object'">-</warning> o2 #fail
    o1 <warning descr="Unresolved attribute reference '*' for class 'object'">*</warning> o2 #fail
    o1 <warning descr="Unresolved attribute reference '/' for class 'object'">/</warning> o2 #fail
    o1 <warning descr="Unresolved attribute reference '%' for class 'object'">%</warning> o2 #fail
    o1 <warning descr="Unresolved attribute reference '**' for class 'object'">**</warning> o2 #fail
    o1 <warning descr="Unresolved attribute reference '<<' for class 'object'"><<</warning> o2 #fail
    o1 <warning descr="Unresolved attribute reference '>>' for class 'object'">>></warning> o2 #fail
    o1 <warning descr="Unresolved attribute reference '&' for class 'object'">&</warning> o2 #fail
    o1 <warning descr="Unresolved attribute reference '|' for class 'object'">|</warning> o2 #fail
    o1 <warning descr="Unresolved attribute reference '^' for class 'object'">^</warning> o2 #fail
    o1 <warning descr="Unresolved attribute reference '//' for class 'object'">//</warning> o2 #fail
    o2 < o1 < o2, o2 <= o1 <= o2, o1 == o2, o1 != o2, o2 in o1 #pass

def test_custom_class():
    c = C()
    o = object()
    c + o, c | o, o - c #pass
    c <warning descr="Unresolved attribute reference '-' for class 'C'">-</warning> o #fail
    c <warning descr="Unresolved attribute reference '*' for class 'C'">*</warning> o #fail
    c <warning descr="Unresolved attribute reference '/' for class 'C'">/</warning> o #fail
    c <warning descr="Unresolved attribute reference '%' for class 'C'">%</warning> o #fail
    c <warning descr="Unresolved attribute reference '**' for class 'C'">**</warning> o #fail
    c <warning descr="Unresolved attribute reference '<<' for class 'C'"><<</warning> o #fail
    c <warning descr="Unresolved attribute reference '>>' for class 'C'">>></warning> o #fail
    c <warning descr="Unresolved attribute reference '&' for class 'C'">&</warning> o #fail
    c <warning descr="Unresolved attribute reference '^' for class 'C'">^</warning> o #fail
    c <warning descr="Unresolved attribute reference '//' for class 'C'">//</warning> o #fail
    o < c < o, o <= c <= o, c == o, c != o, o in c #pass

def test_builtins():
    i = 0
    o = object()
    i + o, i - o, i * o, i / o, i % o, i ** o, i << o, i >> o, i & o, i | o, i ^ o, i // o #pass
    o < i < o, o <= i <= o, i == o, i != o, o in i #pass
    o + i, o - i, o * i, o / i, o % i, o ** i, o << i, o >> i, o & i, o | i, o ^ i, o // i #pass

    s = 'foo'
    s + o, s * o, s % o #pass
    s <warning descr="Unresolved attribute reference '-' for class 'str'">-</warning> o #fail
    s <warning descr="Unresolved attribute reference '/' for class 'str'">/</warning> o #fail
    s <warning descr="Unresolved attribute reference '**' for class 'str'">**</warning> o #fail
    s <warning descr="Unresolved attribute reference '<<' for class 'str'"><<</warning> o #fail
    s <warning descr="Unresolved attribute reference '>>' for class 'str'">>></warning> o #fail
    s <warning descr="Unresolved attribute reference '&' for class 'str'">&</warning> o #fail
    s <warning descr="Unresolved attribute reference '|' for class 'str'">|</warning> o #fail
    s <warning descr="Unresolved attribute reference '^' for class 'str'">^</warning> o #fail
    s <warning descr="Unresolved attribute reference '//' for class 'str'">//</warning> o #fail
    o < s < o, o <= s <= o, s == o, s != o, o in s #pass

    xs = []
    xs + o, xs * o #pass
    xs <warning descr="Unresolved attribute reference '-' for class 'list'">-</warning> o #fail
    xs <warning descr="Unresolved attribute reference '/' for class 'list'">/</warning> o #fail
    xs <warning descr="Unresolved attribute reference '%' for class 'list'">%</warning> o #fail
    xs <warning descr="Unresolved attribute reference '**' for class 'list'">**</warning> o #fail
    xs <warning descr="Unresolved attribute reference '<<' for class 'list'"><<</warning> o #fail
    xs <warning descr="Unresolved attribute reference '>>' for class 'list'">>></warning> o #fail
    xs <warning descr="Unresolved attribute reference '&' for class 'list'">&</warning> o #fail
    xs <warning descr="Unresolved attribute reference '|' for class 'list'">|</warning> o #fail
    xs <warning descr="Unresolved attribute reference '^' for class 'list'">^</warning> o #fail
    xs <warning descr="Unresolved attribute reference '//' for class 'list'">//</warning> o #fail
    o < xs < o, o <= xs <= o, xs == o, xs != o, o in xs #pass
