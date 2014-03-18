from collections import namedtuple

i = namedtuple('Point', ['x', 'y'], verbose=True)
i._replace( **{"a":"a"})


