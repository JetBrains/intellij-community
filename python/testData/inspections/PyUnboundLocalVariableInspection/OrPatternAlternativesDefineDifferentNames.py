def func(x):
    match x:
        case [1, y] | [2, z]:
            print(<warning descr="Local variable 'y' might be referenced before assignment">y</warning>, <warning descr="Local variable 'z' might be referenced before assignment">z</warning>)