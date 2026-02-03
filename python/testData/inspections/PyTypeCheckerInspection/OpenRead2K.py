def f(s):
    """
    :type s: str
    """
    pass

def g(s):
    """
    :type s: int
    """

f(open('foo').read()) # pass
g(<warning descr="Expected type 'int', got 'str' instead">open('foo').read()</warning>)
