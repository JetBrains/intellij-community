def func(x):
    match x:
        case 42:
            y = 'foo'
    print(<warning descr="Local variable 'y' might be referenced before assignment">y</warning>)