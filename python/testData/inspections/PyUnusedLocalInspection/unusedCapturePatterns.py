def func(x):
    match x:
        case [used, <weak_warning descr="Local variable 'unused' value is not used">unused</weak_warning>]:
            print(used)