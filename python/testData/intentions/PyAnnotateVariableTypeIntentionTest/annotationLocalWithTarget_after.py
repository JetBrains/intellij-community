from io import TextIOWrapper, _WrappedBuffer
from typing import Union, Any


def func():
    var: [TextIOWrapper[Union[_WrappedBuffer, Any]]]
    with open('file.txt') as var:
        var
