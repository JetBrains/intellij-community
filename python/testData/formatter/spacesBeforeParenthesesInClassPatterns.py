match x:
    case Class():
        pass
    case Class(foo=1):
        pass
    case Class(foo=1, bar=2):
        pass
    case Class(foo=1, bar=2,):
        pass