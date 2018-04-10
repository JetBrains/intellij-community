from typing import BinaryIO


def func():
    with open('file.txt') as var:  # type: [BinaryIO] # comment
        var
