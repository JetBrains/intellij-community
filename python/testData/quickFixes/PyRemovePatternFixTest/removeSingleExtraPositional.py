class Z:
    __match_args__ = ()

def f(z):
    match z:
        case Z(<warning descr="Too many positional patterns, expected 0"><caret>1</warning>):
            pass
