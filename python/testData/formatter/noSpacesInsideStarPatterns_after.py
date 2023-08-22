match x:
    case (1, *xs):
        pass
    case [1, *_]:
        pass
    case {'foo': 1, **others}:
        pass
