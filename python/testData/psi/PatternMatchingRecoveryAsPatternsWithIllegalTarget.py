match x:
    case 1 as foo.bar:
        pass
    case 1 as foo[0]:
        pass
    case 1 as _:
        pass