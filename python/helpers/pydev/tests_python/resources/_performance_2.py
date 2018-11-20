import time
start_time = time.time()

try:
    xrange  # @UndefinedVariable
except NameError:
    xrange = range
    
from itertools import groupby
from random import randrange

letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'

# create an array of random strings of 40 characters each
l = sorted([''.join([letters[randrange(0, 26)] for _ in range(40)]) for _ in xrange(10000)])

# group by the first two characters
g = {k: list(v) for k, v in groupby(l, lambda x: x[:2])}

print(len(g.get('AA')))

if False:
    pass  # Breakpoint here

print('TotalTime>>%s<<' % (time.time()-start_time,))
print('TEST SUCEEDED')