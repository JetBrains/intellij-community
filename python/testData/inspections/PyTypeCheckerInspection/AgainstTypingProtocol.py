from typing import Protocol


class SupportsClose(Protocol):
    def close(self) -> None:
        pass


class Resource:
    def close(self) -> None:
        pass


def close(closeable: SupportsClose) -> None:
    closeable.close()


f = open("a.txt")
close(f)

r = Resource()
close(r)

# There must be a warning "Expected type 'SupportsClose', got 'type[Resource]' instead".
# To fix this in PyTypeChecker.match(PyCallableType, PyCallableType, MatchContext) should be added checking named, optional and star params, not only positional ones.
# close(Resource)

close(<warning descr="Expected type 'SupportsClose', got 'int' instead">1</warning>)
