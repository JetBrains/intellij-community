from typing import overload
class A:
    @overload
    def __init__(self, **kwargs): ...
    def __init__(self, *args, **kwargs):
        pass