class Empty:
    __match_args__ = ()


def f(obj):
    match obj:
        case Empty(1):
            pass
