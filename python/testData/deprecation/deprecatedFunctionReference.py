from typing_extensions import deprecated


@deprecated("norwegian_blue is deprecated")
def norwegian_blue(x: int) -> int:
    return x


<warning descr="norwegian_blue is deprecated">norwegian_blue</warning>(1)
map(<warning descr="norwegian_blue is deprecated">norwegian_blue</warning>, [1, 2, 3])
