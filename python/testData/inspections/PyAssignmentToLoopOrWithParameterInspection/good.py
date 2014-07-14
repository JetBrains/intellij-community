from spam import eggs

for eggs in (1, 12):
    eggs = 12

for a in (1, 12):
    for b in (2, 24):
        for (c, d) in {"C": "D"}.items():
            (e, f) = (a, d)

i = 12
print(i)
(z, x) = (i, 12)
print(z)

for root in settings.STATICFILES_DIRS:
    if isinstance(root, (list, tuple)):
        prefix, root = root


for field, model in self.model._meta.get_concrete_fields_with_model():
    if model is None:
        model = self.model

with open('a', 'w') as a, open('b', 'w') as b:
    do_something()


for f in [1,2,3]:
    f = f + 1

for f in [1,2,3]:
    f = spam(f)

for f in [1,2,3]:
    f = eggs(lambda x: x + f)

q = []
for q[0] in [1,2,3]:
    q[0] = eggs(q)

q = []
for q[0] in [1,2,3]:
    q[0] = eggs(q)

for f in [1,2,3]:
    f = eggs(lambda x: x, f)

for a in [1,2]:
    pass

for a in [1,2]:
    pass

b = 12
for b in [1,2]:
    pass

for item in range(5):
    want_to_import = False
    print want_to_import
    want_to_import = 2          #No error should be here
    if True:
        pass

for ((a, b), (c, d)) in {(1, 2): (3, 4)}.items():
    print b

x = [1]
for x[0] in range(1,2):
    print i

for x[i] in range(1,2):
    print i

x = [[1]]
for x[0][0] in range(1,2):
    x[0][1] = 1

class Foo(object):
    def __init__(self):
        super(Foo, self).__init__()
        self.data = "ddd"

    def foo(self):
        data, self.data = self.data
        for data in [1,2,3]:
            for self.data in [1,2,3]:
                pass

def contains_even_number(l):
    """
    See: PY-12367
    """
    for elt in range(10):
        print elt
    else:
        for elt in range(10):
            pass