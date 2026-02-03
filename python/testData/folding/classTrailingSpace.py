class Point:<fold text='...'>
    def __init__(self, x=0, y=0):<fold text='...'>
        self.x = x
        self.y = y</fold>

    def distance_from_origin(self):<fold text='...'>
        return (self.x ** 2) + (self.y ** 2) ** 0.5</fold></fold>

# This is a comment
p = Point(3,4)
print "p.x = %d" % (p.x)
print "p.y = %d" % (p.y)