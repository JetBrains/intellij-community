match x:
    case 1 | 2 as x:
        pass
    case 1 as x | 2:
        pass
    case 1 | 2 as x | 3:
        pass