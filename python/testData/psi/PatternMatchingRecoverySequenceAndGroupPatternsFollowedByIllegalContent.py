match x:
    case (1, 2) + (3,):
        pass
    case ((1, 2) + (3,)):
        pass
    case [1, 2] + 3:
        pass
    case [1, 2] + [3]:
        pass
    case [[1, 2] + [3,]]:
        pass
    case (1) * (2 + 3):
        pass
    case ((1 + 2) * 3) / 4:
        pass