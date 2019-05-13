from collections import namedtuple

Point = namedtuple('Point', ['x', 'y'], verbose=True)

print(Point.x, Point.y)

p = Point(11, y=22)
print(p.x + p.y + p.<warning descr="Unresolved attribute reference 'z' for class 'Point'">z</warning>)
print(p.__add__)
print(p._asdict())
print(Point._fields)
print(p._replace)

if isinstance(p, Point):
    p.x

class C(namedtuple('C', 'x y')):
    def f(self):
        return self

c = C()
print(c.x, c.y, c.<warning descr="Unresolved attribute reference 'z' for class 'C'">z</warning>, c.f())
