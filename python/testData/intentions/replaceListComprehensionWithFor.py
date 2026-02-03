a = 2
smth = [x+y+z for x in xrange(10) if x != 2 if x != 5 for y in xrange(12) if y != 1 if y != 4 for z in xrange(14) if z != 3 <caret>if z != 8]

a = 5