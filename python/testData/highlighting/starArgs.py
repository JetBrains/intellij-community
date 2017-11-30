def f1(*args, <error descr="multiple * parameters are not allowed">*</error>, a):
    pass

def f2(*, <error descr="multiple * parameters are not allowed">*</error>, d):
    pass

def f3(*, <error descr="multiple * parameters are not allowed">*args</error>):
    pass
