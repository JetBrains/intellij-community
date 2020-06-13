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
PurePath(<warning descr="Expected type 'str', got 'A' instead">a</warning>)  # TODO ok after supporting versioning in pyi-stubs

os.path.abspath(a)


# not os.PathLike
class B:
    pass

b = B()

open(<warning descr="Expected type 'Union[str, bytes, int, PathLike]', got 'B' instead">b</warning>)

os.fspath(<warning descr="Unexpected type(s):(B)Possible types:(PathLike)(bytes)(str)">b</warning>)
os.fsencode(<warning descr="Unexpected type(s):(B)Possible types:(Union[str, bytes])(Union[str, bytes, PathLike])">b</warning>)
os.fsdecode(<warning descr="Unexpected type(s):(B)Possible types:(Union[str, bytes])(Union[str, bytes, PathLike])">b</warning>)

Path(<warning descr="Unexpected type(s):(B)Possible types:(Union[str, PurePath])(Union[str, PathLike[str]])">b</warning>)
PurePath(<warning descr="Expected type 'str', got 'B' instead">b</warning>)  # TODO update after supporting versioning in pyi-stubs

os.path.abspath(<warning descr="Unexpected type(s):(B)Possible types:(AnyStr)(PathLike)">b</warning>)


# pathlib.PurePath
p = Path(".")

open(p)

os.fspath(p)
os.fsencode(p)
os.fsdecode(p)

Path(p)
PurePath(p)

os.path.abspath(p)