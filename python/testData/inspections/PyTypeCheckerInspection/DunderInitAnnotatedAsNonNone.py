class A:
    def __init__(self) -> <warning descr="__init__ should return None">int</warning>:
        pass


class B:
    def __init__(self, foo):
        <warning descr="__init__ should return None"># type: (str) -> int</warning>
        pass