match x:
    case [x, *args]:
        pass
    case [*ars, z]:
        pass
    case (x, *_, *_):
        pass