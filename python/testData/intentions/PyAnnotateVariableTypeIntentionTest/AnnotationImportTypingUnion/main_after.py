from typing import Union, LiteralString

from lib import foo


def func():
    var: [Union[LiteralString, int]] = foo
    var