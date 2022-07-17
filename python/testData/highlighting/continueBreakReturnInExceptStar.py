def foo(x):
    for i in range(0, x):
        try:
            ...
        except* TypeError:
            if x == 1:
                <error descr="'break', 'continue' and 'return' cannot appear in an except* block">return 1</error>
            if x == 2:
                <error descr="'break', 'continue' and 'return' cannot appear in an except* block">continue</error>
            if x == 3:
                <error descr="'break', 'continue' and 'return' cannot appear in an except* block">break</error>

