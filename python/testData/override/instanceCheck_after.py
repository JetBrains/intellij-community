from typing import Any


class MyType(type):
    def __instancecheck__(self, __instance: Any) -> bool:
        <selection>return super().__instancecheck__(__instance)</selection>
