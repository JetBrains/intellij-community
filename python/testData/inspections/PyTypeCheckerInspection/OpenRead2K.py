def f(s):
    """
    :type s: str
    """
    pass

def g(s):
    """
    :type s: unicode
    """

f(open('foo').read()) # pass
g(<warning descr="Expected type 'unicode', got 'str' instead">open('foo').read()</warning>)
