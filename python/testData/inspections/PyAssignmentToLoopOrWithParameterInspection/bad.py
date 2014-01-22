i = []
for i[0] in xrange(5):
    for <warning descr="Assignment to 'for' loop or 'with' statement parameter">i[0]</warning> in xrange(20, 25):
        print("Inner", i)
        for <warning descr="Assignment to 'for' loop or 'with' statement parameter">i</warning> in xrange(20, 25):
            pass
    print("Outer", i)

for i in xrange(5):
    for <warning descr="Assignment to 'for' loop or 'with' statement parameter">i</warning> in xrange(20, 25):
        print("Inner", i)
    print("Outer", i)

for i in xrange(5):
    i = []
    for <warning descr="Assignment to 'for' loop or 'with' statement parameter">i[0]</warning> in xrange(20, 25):
        print("Inner", i)
    print("Outer", i)

i = [0]
for i[0] in xrange(5):
    for <warning descr="Assignment to 'for' loop or 'with' statement parameter">i[0]</warning> in xrange(20, 25):
        print("Inner", i)
    print("Outer", i)

i = [[]]
for i[0] in xrange(5):
    for <warning descr="Assignment to 'for' loop or 'with' statement parameter">i</warning> in xrange(20, 25):
        print("Inner", i)
    print("Outer", i)

with open("a") as f:
    spam(f)
    f.eggs()
    with open("b") as <warning descr="Assignment to 'for' loop or 'with' statement parameter">f</warning>: #
        pass

with open("a") as z, open("A") as f:
    spam(f)
    f.eggs()
    for (a,b,c,d,(e,<warning descr="Assignment to 'for' loop or 'with' statement parameter">f</warning>)) in []:
        pass


with open("a") as f:
    spam(f)
    f.eggs()
    for z in []:
        with open("b") as q:
            with open("a") as <warning descr="Assignment to 'for' loop or 'with' statement parameter">f</warning>: #
                pass


class Foo(object):
    def __init__(self):
        super(Foo, self).__init__()
        self.data = "ddd"

    def foo(self):
        for self.data in [1,2,3]:
            for <warning descr="Assignment to 'for' loop or 'with' statement parameter">self.data</warning> in [1,2,3]:
                pass