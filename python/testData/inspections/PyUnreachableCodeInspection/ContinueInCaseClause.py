def func(xs):
    for x in xs:
        match x:
            case 42:
                continue
        print(x)