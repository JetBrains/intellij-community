def f1(*args, <error descr="Multiple * arguments are not allowed">*</error>, a):
    pass

def f2(*, <error descr="Multiple * arguments are not allowed">*</error>, d):
    pass

def f3(*, <error descr="Multiple * arguments are not allowed">*args</error>):
    pass
