from typing import TypeVar, Iterator

T = TypeVar('T')
class MyIterator(Iterator[T]):
    def __init__(self) -> None:
        self.other = "other"