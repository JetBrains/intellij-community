def f(x) -> int | None:
    if x == 1:
        return 42
    elif x == 2:
        <warning descr="Function returning 'int | None' has implicit 'return None'">return<caret></warning>
    elif x == 3:
        pass