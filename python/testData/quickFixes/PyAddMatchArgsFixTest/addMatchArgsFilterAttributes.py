class C:
    def __init__(self, a, b, c):
        self.a = a
        # b is not assigned to instance attribute

def f(obj):
    match obj:
        case C(<warning descr="Class C does not support pattern matching with positional arguments"><caret>1</warning>, <warning descr="Class C does not support pattern matching with positional arguments">2</warning>):
            pass
