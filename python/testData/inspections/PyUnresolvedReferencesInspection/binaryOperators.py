class C(object):
    def __add__(self, other):
        return int(other)

    def __or__(self, other):
        return other

def test_object():
    o = object()
    o <warning descr="Unresolved attribute reference '+' for class 'object'">+</warning> 1 #fail
    o <warning descr="Unresolved attribute reference '-' for class 'object'">-</warning> 1 #fail
    o <warning descr="Unresolved attribute reference '*' for class 'object'">*</warning> 1 #fail
    o <warning descr="Unresolved attribute reference '/' for class 'object'">/</warning> 1 #fail
    o <warning descr="Unresolved attribute reference '%' for class 'object'">%</warning> 1 #fail
    o <warning descr="Unresolved attribute reference '**' for class 'object'">**</warning> 1 #fail
    o <warning descr="Unresolved attribute reference '<<' for class 'object'"><<</warning> 1 #fail
    o <warning descr="Unresolved attribute reference '>>' for class 'object'">>></warning> 1 #fail
    o <warning descr="Unresolved attribute reference '&' for class 'object'">&</warning> 1 #fail
    o <warning descr="Unresolved attribute reference '|' for class 'object'">|</warning> 1 #fail
    o <warning descr="Unresolved attribute reference '^' for class 'object'">^</warning> 1 #fail
    o <warning descr="Unresolved attribute reference '//' for class 'object'">//</warning> 1 #fail
    1 < o < 1, 1 <= o <= 1, o == 1, o != 1, 1 in o #pass

def test_custom_class():
    c = C()
    c + 1, c | 1 #pass
    c <warning descr="Unresolved attribute reference '-' for class 'C'">-</warning> 1 #fail
    c <warning descr="Unresolved attribute reference '*' for class 'C'">*</warning> 1 #fail
    c <warning descr="Unresolved attribute reference '/' for class 'C'">/</warning> 1 #fail
    c <warning descr="Unresolved attribute reference '%' for class 'C'">%</warning> 1 #fail
    c <warning descr="Unresolved attribute reference '**' for class 'C'">**</warning> 1 #fail
    c <warning descr="Unresolved attribute reference '<<' for class 'C'"><<</warning> 1 #fail
    c <warning descr="Unresolved attribute reference '>>' for class 'C'">>></warning> 1 #fail
    c <warning descr="Unresolved attribute reference '&' for class 'C'">&</warning> 1 #fail
    c <warning descr="Unresolved attribute reference '^' for class 'C'">^</warning> 1 #fail
    c <warning descr="Unresolved attribute reference '//' for class 'C'">//</warning> 1 #fail
    1 < c < 1, 1 <= c <= 1, c == 1, c != 1, 1 in c #pass

def test_builtins():
    i = 0
    i + 1, i - 1, i * 1, i / 1, i % 1, i ** 1, i << 1, i >> 1, i & 1, i | 1, i ^ 1, i // 1 #pass
    1 < i < 1, 1 <= i <= 1, i == 1, i != 1, 1 in i #pass

    s = 'foo'
    s + 'a', s * 1, s % 'a' #pass
    s <warning descr="Unresolved attribute reference '-' for class 'str'">-</warning> 'a' #fail
    s <warning descr="Unresolved attribute reference '/' for class 'str'">/</warning> 1 #fail
    s <warning descr="Unresolved attribute reference '**' for class 'str'">**</warning> 1 #fail
    s <warning descr="Unresolved attribute reference '<<' for class 'str'"><<</warning> 1 #fail
    s <warning descr="Unresolved attribute reference '>>' for class 'str'">>></warning> 1 #fail
    s <warning descr="Unresolved attribute reference '&' for class 'str'">&</warning> 1 #fail
    s <warning descr="Unresolved attribute reference '|' for class 'str'">|</warning> 1 #fail
    s <warning descr="Unresolved attribute reference '^' for class 'str'">^</warning> 1 #fail
    s <warning descr="Unresolved attribute reference '//' for class 'str'">//</warning> 1 #fail
    'a' < s < 'a', 'a' <= s <= 'a', s == 'a', s != 'a', 'a' in s #pass

    xs = []
    xs + [], xs * 1 #pass
    xs <warning descr="Unresolved attribute reference '-' for class 'list'">-</warning> 'a' #fail
    xs <warning descr="Unresolved attribute reference '/' for class 'list'">/</warning> 1 #fail
    xs <warning descr="Unresolved attribute reference '%' for class 'list'">%</warning> 1 #fail
    xs <warning descr="Unresolved attribute reference '**' for class 'list'">**</warning> 1 #fail
    xs <warning descr="Unresolved attribute reference '<<' for class 'list'"><<</warning> 1 #fail
    xs <warning descr="Unresolved attribute reference '>>' for class 'list'">>></warning> 1 #fail
    xs <warning descr="Unresolved attribute reference '&' for class 'list'">&</warning> 1 #fail
    xs <warning descr="Unresolved attribute reference '|' for class 'list'">|</warning> 1 #fail
    xs <warning descr="Unresolved attribute reference '^' for class 'list'">^</warning> 1 #fail
    xs <warning descr="Unresolved attribute reference '//' for class 'list'">//</warning> 1 #fail
    'a' < xs < 'a', 'a' <= xs <= 'a', xs == 'a', xs != 'a', 'a' in xs #pass
