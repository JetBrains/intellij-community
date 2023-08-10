match x:
    case [1, 2,]:
        pass
    case (1, 2,):
        pass
    case {'foo': 1, 'bar': 2,}:
        pass
    case Class(foo=1, bar=2,):
        pass