match x:
    case 1 if x > 1:
        pass
    case [1, y] if y + (1 if y > 42 else 0) > 2:
        pass