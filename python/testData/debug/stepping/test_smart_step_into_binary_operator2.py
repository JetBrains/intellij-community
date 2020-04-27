class Point(object):
    def __init__(self, x, y):
        self.x = x
        self.y = y

    def __add__(self, other):
        return Point(self.x + other.x, self.y + other.y)

    def __sub__(self, other):
        return Point(self.x - other.x, self.y - other.y)


p1 = Point(1, 1)
p2 = Point(2, 2)
p3 = Point(3, 3)

p = p1 + p2 - p3 - Point(4, 4) + Point(5, 5)
