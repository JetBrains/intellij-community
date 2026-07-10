def foo(*args):
    """
    :type args: str
    """
    pass


foo(<warning descr="Expected type 'str', got 'Literal[1]' instead">1</warning>, '1')
