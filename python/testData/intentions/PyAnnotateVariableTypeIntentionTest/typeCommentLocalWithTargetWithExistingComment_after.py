from io import TextIOWrapper, _WrappedBuffer


def func():
    with open('file.txt') as var:  # type: [TextIOWrapper[_WrappedBuffer]] # comment
        var
