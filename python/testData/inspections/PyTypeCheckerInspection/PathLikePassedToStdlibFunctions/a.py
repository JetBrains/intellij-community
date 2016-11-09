import os
import os.path


# os.PathLike
class A:
    def __fspath__(self):
        pass

a = A()

open(a)

os.fspath(a)
os.fsencode(a)
os.fsdecode(a)

os.path.abspath(a)


# not os.PathLike
class B:
    pass

b = B()

open(<warning descr="Expected type 'Union[str, PathLike]', got 'B' instead">b</warning>)

os.fspath(b)  # TODO fail
os.fsencode(b)  # TODO fail
os.fsdecode(b)  # TODO fail

os.path.abspath(<warning descr="Expected type 'Union[bytes, str, PathLike]', got 'B' instead">b</warning>)