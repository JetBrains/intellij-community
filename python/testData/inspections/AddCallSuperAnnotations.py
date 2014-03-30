class Example1:
    def __init__(self, field1: str):
        self.field1 = field1


class Example2(Example1):
    def <warning descr="Call to __init__ of super class is missed">__i<caret>nit__</warning>(self):  ## Missed call to __init__ of super class
        pass