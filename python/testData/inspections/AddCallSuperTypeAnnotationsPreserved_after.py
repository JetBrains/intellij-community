class A:
    def __init__(self, a:int, b:float, *args:tuple, c:complex, **kwargs:dict) -> None:
        pass

class B(A):
    def __init__(self, d: str, a: int, b: float, *args: tuple, e: bytes, c: complex, **kwargs: dict) -> list:
        super().__init__(a, b, *args, c=c, **kwargs)