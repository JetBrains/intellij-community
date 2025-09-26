class Empty:
    __match_args__ = ()
    pass

def f(obj):
    match obj:
        case Empty(1):
            pass
