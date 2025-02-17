def f(x) -> int | None:
    y = 42
    print(y)
    <weak_warning descr="Explicit return statement expected">if x == 1:
        return 42
    elif x == 2:
        <weak_warning descr="Explicit return value expected">return<caret></weak_warning>
    elif x == 3:
        raise Exception()
    elif x == 4:
        assert False
    elif x == 5:
        <weak_warning descr="Explicit return statement expected">assert x</weak_warning>
    elif x == 4:
        <weak_warning descr="Explicit return statement expected">pass</weak_warning></weak_warning>