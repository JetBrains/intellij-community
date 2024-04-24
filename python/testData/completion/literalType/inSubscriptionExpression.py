from typing import Literal

class A:
    def __getitem__(self, item: Literal[Literal[Literal[1, 2, 3], "foo"], 5, None]) -> str:
        pass


A()[<caret>]