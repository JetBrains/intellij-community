from collections import namedtuple
foofields = 'bar', 'xyzzy'
nt = namedtuple('foo', foofields)
o = nt(1, 2)
print o.xyzzy