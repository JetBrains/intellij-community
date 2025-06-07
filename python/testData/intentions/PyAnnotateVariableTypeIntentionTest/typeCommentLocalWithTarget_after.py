from io import TextIOWrapper, _WrappedBuffer
from typing import Union, Any


def func():
    with open('file.txt') as var:  # type: [TextIOWrapper[Union[_WrappedBuffer, Any]]]
        var
