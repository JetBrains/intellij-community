class C:
    def __add__(self, other: C) -> C: ...

    def __sub__(self, other: D) -> <error descr="Unresolved reference 'NonExisting'">NonExisting</error>: ...


class D:
    pass
