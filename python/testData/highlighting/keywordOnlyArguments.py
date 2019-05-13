def keywordonly_sum(*, k1=0, k2):
    return k1 + k2

def keywordonly_sum_bad1(p, <error descr="named parameters must follow bare *">*</error>):
    pass

def keywordonly_sum_bad2(p1, *, <error descr="named parameters must follow bare *">**k1</error>):
    pass

def keywordonly_and_kwarg_sum(*, k1, k2, **kwarg):
    return k1 + k2 + sum(kwarg.values())

def keywordonly_sum_bad2(p, *, <error descr="tuple parameter unpacking is not supported in Python 3">(k1, k2)</error>, **kw):
    pass
