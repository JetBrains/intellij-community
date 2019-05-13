class String(bytes):
    pass


def foo(x):
    """
    :type x: str
    """

s = String('hello')
foo(s)
