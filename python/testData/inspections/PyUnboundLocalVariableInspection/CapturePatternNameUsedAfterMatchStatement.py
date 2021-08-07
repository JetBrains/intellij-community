def func(x):
    match x:
        case 42 as y:
            pass
        case z:
            pass
    print(<warning descr="Local variable 'y' might be referenced before assignment">y</warning>, <warning descr="Local variable 'z' might be referenced before assignment">z</warning>)
