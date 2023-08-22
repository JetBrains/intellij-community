match x:
    case x as:
        pass
    case [1 as ]:
        pass
    case [1 as,]:
        pass
    case [1 as, 2]:
        pass
    case (1 as ):
        pass
    case (1 as,):
        pass
    case (1 as, 2):
        pass
    case C(1 as):
        pass
    case C(1 as,):
        pass
    case C(1 as, 2):
        pass
    case {'foo': 1 as}:
        pass
    case {'foo': 1 as,}:
        pass
    case {'foo': 1 as, 'bar': 2}:
        pass
    case {'foo' as : 1}:
        pass