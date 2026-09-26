match x:
    case C(1, 2) + C(3):
        pass
    case C(C(1, 2).foo):
        pass