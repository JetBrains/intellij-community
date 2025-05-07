class A:
    def __init__(self) -> None:
        pass

    @property
    def a_property(self) -> str:
        return 'foo'


class B:
    def __init__(self, a: A) -> None:
        self.b_attr = a.a_property
