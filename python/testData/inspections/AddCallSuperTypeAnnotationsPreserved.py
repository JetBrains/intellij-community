class A:
    def __init__(self, a:int, b:float, *args:tuple, c:complex, **kwargs:dict) -> None:
        pass

class B(A):
    def <warning descr="Call to __init__ of super class is missed">__i<caret>nit__</warning>(self, d:str, *, e:bytes) -> list:
        pass