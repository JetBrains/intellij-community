SUFFIX = "foo"
from sys import version

a = version + SUFFIX


def func():
    print(a)