1, *x
(1, *x)
[1, *x]
{1, *x}

if <error descr="Can't use starred expression here">*x</error>:
    pass

1 + (<error descr="Can't use starred expression here">*x</error>)
1 + (*x,)