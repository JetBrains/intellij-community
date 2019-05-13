pyt = (<fold text='...'>(x, y, z) for z in b
       for y in xrange(1, z)
       for x in range(1, y) if x * x + y * y == z * z</fold>)

primes = {<fold text='...'>x for x in range(2, 101)
          if all(x % y for y in range(2, min(x, 11)))</fold>}

d = {<fold text='...'>n: n ** 2
     for n in range(5)</fold>}