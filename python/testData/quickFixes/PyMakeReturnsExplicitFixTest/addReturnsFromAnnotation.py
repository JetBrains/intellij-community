def f(x) -> <warning descr="Function returning 'int | None' has implicit return">int | None<caret></warning>:
    if x == 1:
        return 42
    elif x == 2:
        <warning descr="Function returning 'int | None' has implicit return">return</warning>