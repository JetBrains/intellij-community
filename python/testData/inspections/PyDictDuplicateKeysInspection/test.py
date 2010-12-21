dict = <warning descr="Dictionary contains duplicate keys key_1">{key_1 : 1, key_2: 2, key_1 : 3}</warning>
dict = <warning descr="Dictionary contains duplicate keys 'key_2'">{'key_1' : 1, 'key_2': 2, 'key_2' : 3}</warning>
a = {}
{'key_1' : 1, 'key_2': 2}

import random
def foo():
    return random.random()

{foo(): 1, foo():2}

# PY-2511
dict = <warning descr="Dictionary contains duplicate keys key">dict([('key', 666), ('key', 123)])</warning>
dict = <warning descr="Dictionary contains duplicate keys key">dict((('key', 666), ('key', 123)))</warning>
dict = <warning descr="Dictionary contains duplicate keys key">dict((('key', 666), ('k', 123)), key=4)</warning>

dict([('key', 666), ('ky', 123)])
