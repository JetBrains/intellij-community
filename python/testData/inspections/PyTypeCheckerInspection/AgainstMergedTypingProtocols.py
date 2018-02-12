from typing import Protocol, Sized


class SupportsClose(Protocol):
    def close(self) -> None:
        pass


class SizedAndClosable(Sized, SupportsClose, Protocol):
    pass


class Resource:
    def __len__(self) -> int:
        return 0

    def close(self) -> None:
        pass


def close(sized_and_closeable: SizedAndClosable) -> None:
    print(len(sized_and_closeable))
    sized_and_closeable.close()


r = Resource()
close(r)

close(<warning descr="Expected type 'SizedAndClosable', got 'int' instead">1</warning>)
