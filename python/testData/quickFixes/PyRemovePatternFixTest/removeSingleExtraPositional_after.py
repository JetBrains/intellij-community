class Z:
    __match_args__ = ()

def f(z):
    match z:
        case Z():
            pass
