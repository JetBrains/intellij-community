def valid1(p1, p2, /, p_or_kw, *, kw):
    pass
def valid2(p1, p2=None, /, p_or_kw=None, *, kw):
    pass
def valid3(p1, p2=None, /, *, kw):
    pass
def valid4(p1, p2=None, /):
    pass
def valid5(p1, p2, /, p_or_kw):
    pass
def valid6(p1, p2, /):
    pass

def valid7(p_or_kw, *, kw):
    pass
def valid8(*, kw):
    pass

def invalid1(pos, *args, <error descr="/ parameter must precede * parameter">/</error>, pos_or_kwd):
    pass
def invalid2(pos, /, pos_or_kwd1, <error descr="multiple / parameters are not allowed">/</error>, pos_or_kwd2):
    pass
def invalid3(pos, **kwargs, <error descr="/ parameter must precede ** parameter">/</error>):
    pass
def invalid4(<error descr="named parameters must precede bare /">/</error>):
    pass
def invalid5(<error descr="named parameters must precede bare /">/</error>, p):
    pass