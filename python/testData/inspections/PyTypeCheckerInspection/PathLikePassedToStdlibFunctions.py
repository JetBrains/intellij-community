import os.path
from pathlib import Path, PurePath


# os.PathLike
class A:
    def __fspath__(self) -> str:
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

open(<warning descr="Expected type 'int | str | bytes | PathLike[str] | PathLike[bytes]', got 'B' instead">b</warning>)

os.fspath(<warning descr="No overload of 'fspath' matches the arguments. Argument types: (B). Expected one of: (path: str), (path: bytes), (path: PathLike[AnyStr])">b</warning>)
os.fsencode(<warning descr="Expected type 'str | bytes | PathLike[str] | PathLike[bytes]', got 'B' instead">b</warning>)
os.fsdecode(<warning descr="Expected type 'str | bytes | PathLike[str] | PathLike[bytes]', got 'B' instead">b</warning>)

Path(<warning descr="Expected type 'str | PathLike[str]', got 'B' instead">b</warning>)
PurePath(<warning descr="Expected type 'str | PathLike[str]', got 'B' instead">b</warning>)

os.path.abspath(<warning descr="No overload of 'abspath' matches the arguments. Argument types: (B). Expected one of: (path: PathLike[AnyStr]), (path: AnyStr)">b</warning>)


# pathlib.PurePath
p = Path(".")

open(p)

os.fspath(p)
os.fsencode(p)
os.fsdecode(p)

Path(p)
PurePath(p)

os.path.abspath(p)