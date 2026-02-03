match x:
    case C(:
        pass
    case C(1:
        pass
    case C(1,:
        pass
    case pkg.mod.():
        pass
    case C(D(1, 2):
        pass
    case C(1, D(2):
        pass
    case C(attr=):
        pass
    case C(attr=,):
        pass