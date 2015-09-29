def foo(*args, **kwargs):
    print(args, kwargs)


foo(0,
    *[1],
    <warning descr="Python versions < 3.5 do not allow positional arguments after *expression">2</warning>,
    <warning descr="Python versions < 3.5 do not allow duplicate *expressions">*[3]</warning>,
    <warning descr="Python versions < 3.5 do not allow positional arguments after *expression">4</warning>,
    <warning descr="Python versions < 2.6 do not allow keyword arguments after *expression">a='a'</warning>,
    <warning descr="Python versions < 3.5 do not allow duplicate *expressions">*[6]</warning>,
    <warning descr="Python versions < 2.6 do not allow keyword arguments after *expression">b='b'</warning>,
    <warning descr="Python versions < 3.5 do not allow duplicate *expressions">*[7]</warning>,
    <warning descr="Python versions < 2.6 do not allow keyword arguments after *expression">c='c'</warning>,
    **{'d': 'd'},
    <warning descr="Python versions < 2.6 do not allow keyword arguments after *expression">e='e'</warning>,
    <warning descr="Python versions < 3.5 do not allow duplicate **expressions">**{'f': 'f'}</warning>)
