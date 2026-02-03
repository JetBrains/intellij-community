def keywordonly_sum(*, k1=0, k2):
    return k1 + k2

keywordonly_sum(k2=1)
keywordonly_sum(k1=1, k2=2)
keywordonly_sum(<warning descr="Parameter 'k2' unfilled">)</warning>
keywordonly_sum(<warning descr="Unexpected argument">1</warning>, <warning descr="Unexpected argument">2</warning><warning descr="Parameter 'k2' unfilled">)</warning>

def namedpast(*args, foo=None):
    pass

namedpast(1,2,3, foo='a') # pass
namedpast(*args, foo='b') # pass
namedpast(foo='c') # pass
namedpast() # pass
namedpast(foo='1', <error descr="Positional argument after keyword argument">2</error>) # fail

def a23(a, *b, c=1):
    pass

a23(1,2,3, c=10) # pass
a23(1,2,3, c=10, <warning descr="Unexpected argument">a=1</warning>) # fail
a23(c=10, a=1) # pass
a23(c=10, <error descr="Positional argument after keyword argument">1</error><warning descr="Parameter 'a' unfilled">)</warning> # fail
a23(*args, c=1) # pass

