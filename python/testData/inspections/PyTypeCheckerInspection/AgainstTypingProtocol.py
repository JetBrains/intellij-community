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
close(<warning descr="Expected type 'SupportsClose', got 'Type[Resource]' instead">Resource</warning>)

close(<warning descr="Expected type 'SupportsClose', got 'int' instead">1</warning>)
