from collections import namedtuple

Point = namedtuple('Point', 'x y')
p = Point(1, 2)

# Fields and unpacking
x, y = p
x, y, z = p
print(p.x, p.y, p.<warning descr="Unresolved attribute reference 'z' for class 'Point'">z</warning>)
print(Point.x, Point.y, Point.<warning descr="Unresolved attribute reference 'z' for class 'Point'">z</warning>)

# Tuple attributes
print(p.count(1), p.__class__, p.__add__((1, 2)))

# Named tuple attribute
print(Point.__slots__, Point._fields)
print(p.__module__, p.__slots__)
print(p._asdict(), p._fields, p._replace(x=42))
