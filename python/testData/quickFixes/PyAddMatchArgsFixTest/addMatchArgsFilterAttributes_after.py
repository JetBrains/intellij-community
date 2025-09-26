class C:
    __match_args__ = ('a',)

    def __init__(self, a, b, c):
        self.a = a
        # b is not assigned to instance attribute

def f(obj):
    match obj:
        case C(1, 2):
            pass
