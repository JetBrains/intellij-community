match x:
    case C():
        pass
    case pkg.mod.C(1, 2):
        pass
    case C(1, 2,):
        pass
    case C(D(1), 2):
        pass
    case C(attr=1):
        pass
    case C(1, attr=2):
        pass
    case C(1, attr=2,):
        pass