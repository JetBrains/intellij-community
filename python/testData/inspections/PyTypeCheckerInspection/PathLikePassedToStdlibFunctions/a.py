import os
import os.path
from pathlib import Path, PurePath


# os.PathLike
class A:
    def __fspath__(self):
        pass

a = A()

open(a)

os.fspath(a)
os.fsencode(a)
os.fsdecode(a)

Path(a)
PurePath(a)

os.path.abspath(a)


# not os.PathLike
class B:
    pass

b = B()

open(<warning descr="Unexpected type(s):(B)Possible types:(Union[str, bytes, int])(Union[str, bytes, int, PathLike])">b</warning>)

os.fspath(b)  # TODO fail after enabling pyi-stubs for `os` module
os.fsencode(b)  # TODO fail after enabling pyi-stubs for `os` module
os.fsdecode(b)  # TODO fail after enabling pyi-stubs for `os` module

Path(<warning descr="Expected type 'Union[str, bytes, PathLike]', got 'B' instead">b</warning>)
PurePath(<warning descr="Expected type 'Union[str, bytes, PathLike]', got 'B' instead">b</warning>)

os.path.abspath(<warning descr="Expected type 'Union[bytes, str, PathLike]', got 'B' instead">b</warning>)


# pathlib.PurePath
p = Path(".")

open(p)

os.fspath(p)
os.fsencode(p)
os.fsdecode(p)

Path(p)
PurePath(p)

os.path.abspath(p)