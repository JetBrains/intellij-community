class Point:
    __match_args__ = ("x", "y")

def f(p: Point):
    match p:
        case Point(/*<# x= #>*/1, /*<# y= #>*/2):
            pass
