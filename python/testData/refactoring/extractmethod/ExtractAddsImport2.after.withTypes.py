from io import TextIOWrapper, _WrappedBuffer


def f(a):
    file = open("test.txt", "w")
    body(file)


def body(file_new: TextIOWrapper[_WrappedBuffer]):
    1
    file_new
