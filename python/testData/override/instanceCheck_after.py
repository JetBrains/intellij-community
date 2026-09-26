from typing import Any


class MyType(type):
    def __instancecheck__(self, instance: Any, /) -> bool:
        <selection>return super().__instancecheck__(instance)</selection>
