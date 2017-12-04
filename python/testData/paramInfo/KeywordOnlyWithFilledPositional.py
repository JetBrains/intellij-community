def foo(*, kw1, kw2):
    print(kw1, kw2)


foo(<arg1>1, 2, kw1=1, kw2=2)
foo(1, <arg2>2, kw1=1, kw2=2)
foo(1, 2, <arg3>kw1=1, kw2=2)
foo(1, 2, kw1=1, <arg4>kw2=2)