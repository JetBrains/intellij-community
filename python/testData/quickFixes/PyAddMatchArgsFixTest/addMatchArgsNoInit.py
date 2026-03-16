class Empty:
    pass

def f(obj):
    match obj:
        case Empty(<warning descr="Class Empty does not support pattern matching with positional arguments"><caret>1</warning>):
            pass
