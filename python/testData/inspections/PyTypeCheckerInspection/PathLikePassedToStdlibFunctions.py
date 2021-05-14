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

open(<warning descr="Expected type 'Union[str, bytes, PathLike[str], PathLike[bytes], int]', got 'B' instead">b</warning>)

os.fspath(<warning descr="Unexpected type(s):(B)Possible type(s):(PathLike[Union[Union[str, bytes], Any]])(bytes)(str)">b</warning>)
os.fsencode(<warning descr="Expected type 'Union[str, bytes, PathLike]', got 'B' instead">b</warning>)
os.fsdecode(<warning descr="Expected type 'Union[str, bytes, PathLike]', got 'B' instead">b</warning>)

Path(<warning descr="Expected type 'Union[str, PathLike[str]]', got 'B' instead">b</warning>)
PurePath(<warning descr="Expected type 'Union[str, PathLike[str]]', got 'B' instead">b</warning>)

os.path.abspath(<warning descr="Unexpected type(s):(B)Possible type(s):(AnyStr)(PathLike[Union[Union[str, bytes], Any]])">b</warning>)


# pathlib.PurePath
p = Path(".")

open(p)

os.fspath(p)
os.fsencode(p)
os.fsdecode(p)

Path(p)
PurePath(p)

os.path.abspath(p)