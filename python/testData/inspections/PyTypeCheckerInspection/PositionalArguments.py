def foo(*args):
    """
    :type args: str
    """
    pass


foo(<warning descr="Expected type 'str', got 'int' instead">1</warning>, '1')
