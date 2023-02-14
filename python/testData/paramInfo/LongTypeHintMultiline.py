from typing import Literal


class MyClassWithVeryVeryVeryLongName:
    pass


Upper = Literal["A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
                "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"]


def multiline(parameter: MyClassWithVeryVeryVeryLongName |
                         Upper |
                         int, short_param: str):
    pass


multiline(<arg1>)

multiline(1, <arg2>)

