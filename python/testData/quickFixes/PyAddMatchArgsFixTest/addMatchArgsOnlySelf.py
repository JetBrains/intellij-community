class OnlySelf:
    def __init__(self):
        pass

def f(obj):
    match obj:
        case OnlySelf(<warning descr="Class OnlySelf does not support pattern matching with positional arguments"><caret>1</warning>):
            pass
