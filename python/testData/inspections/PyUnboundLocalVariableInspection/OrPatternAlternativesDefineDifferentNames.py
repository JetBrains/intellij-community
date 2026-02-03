def func(x):
    match x:
        case <error descr="Pattern does not bind name z">[1, y]</error> | <error descr="Pattern does not bind name y">[2, z]</error>:
            print(<warning descr="Local variable 'y' might be referenced before assignment">y</warning>, <warning descr="Local variable 'z' might be referenced before assignment">z</warning>)