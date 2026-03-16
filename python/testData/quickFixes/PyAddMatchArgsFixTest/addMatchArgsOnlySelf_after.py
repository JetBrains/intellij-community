class OnlySelf:
    __match_args__ = ()

    def __init__(self):
        pass

def f(obj):
    match obj:
        case OnlySelf(1):
            pass
