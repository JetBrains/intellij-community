i = []
for i[0] in xrange(5):
    for <weak_warning descr="Variable 'i[0]' already declared in 'for' loop or 'with' statement above">i[0]</weak_warning> in xrange(20, 25):
        print("Inner", i)
        for <weak_warning descr="Variable 'i' already declared in 'for' loop or 'with' statement above">i</weak_warning> in xrange(20, 25):
            pass
    print("Outer", i)

for i in xrange(5):
    for <weak_warning descr="Variable 'i' already declared in 'for' loop or 'with' statement above">i</weak_warning> in xrange(20, 25):
        print("Inner", i)
    print("Outer", i)

for i in xrange(5):
    i = []
    for <weak_warning descr="Variable 'i[0]' already declared in 'for' loop or 'with' statement above">i[0]</weak_warning> in xrange(20, 25):
        print("Inner", i)
    print("Outer", i)

i = [0]
for i[0] in xrange(5):
    for <weak_warning descr="Variable 'i[0]' already declared in 'for' loop or 'with' statement above">i[0]</weak_warning> in xrange(20, 25):
        print("Inner", i)
    print("Outer", i)

i = [[]]
for i[0] in xrange(5):
    for <weak_warning descr="Variable 'i' already declared in 'for' loop or 'with' statement above">i</weak_warning> in xrange(20, 25):
        print("Inner", i)
    print("Outer", i)

with open("a") as f:
    spam(f)
    f.eggs()
    with open("b") as <weak_warning descr="Variable 'f' already declared in 'for' loop or 'with' statement above">f</weak_warning>: #
        pass

with open("a") as z, open("A") as f:
    spam(f)
    f.eggs()
    for (a,b,c,d,(e,<weak_warning descr="Variable 'f' already declared in 'for' loop or 'with' statement above">f</weak_warning>)) in []:
        pass


with open("a") as f:
    spam(f)
    f.eggs()
    for z in []:
        with open("b") as q:
            with open("a") as <weak_warning descr="Variable 'f' already declared in 'for' loop or 'with' statement above">f</weak_warning>: #
                pass


class Foo(object):
    def __init__(self):
        super(Foo, self).__init__()
        self.data = "ddd"

    def foo(self):
        for self.data in [1,2,3]:
            for <weak_warning descr="Variable 'self.data' already declared in 'for' loop or 'with' statement above">self.data</weak_warning> in [1,2,3]:
                pass

for elt in range(10):
    print elt
else:
    for elt in range(10):
        for <weak_warning descr="Variable 'elt' already declared in 'for' loop or 'with' statement above">elt</weak_warning>  in range(10):
            pass

for elt in range(10):
    for <weak_warning descr="Variable 'elt' already declared in 'for' loop or 'with' statement above">elt</weak_warning>  in range(10):
        pass
else:
    for elt in range(10):
        pass