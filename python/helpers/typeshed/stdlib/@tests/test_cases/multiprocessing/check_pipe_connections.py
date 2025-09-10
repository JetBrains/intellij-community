from __future__ import annotations

import sys
from multiprocessing.connection import Pipe

if sys.platform != "win32":
    from multiprocessing.connection import Connection
else:
    from multiprocessing.connection import PipeConnection as Connection


# Unfortunately, we cannot validate that both connections have the same, but inverted generic types,
# since TypeVars scoped entirely within a return annotation is unspecified in the spec.
# Pipe[str, int]() -> tuple[Connection[str, int], Connection[int, str]]

a: Connection[str, int]
b: Connection[int, str]
a, b = Pipe()

connections: tuple[Connection[str, int], Connection[int, str]] = Pipe()
a, b = connections

a.send("test")
a.send(0)  # type: ignore
test1: str = b.recv()
test2: int = b.recv()  # type: ignore

b.send("test")  # type: ignore
b.send(0)
test3: str = a.recv()  # type: ignore
test4: int = a.recv()
