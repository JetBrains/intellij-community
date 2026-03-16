1, *x
(1, *x)
[1, *x]
{1, *x}

if <error descr="Cannot use starred expression here">*x</error>:
    pass

1 + (<error descr="Cannot use starred expression here">*x</error>)
1 + (*x,)
