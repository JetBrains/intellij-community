def foo(**kwargs):
    """
    :type kwargs: int
    """
    pass

foo(key1=10, <warning descr="Expected type 'int', got 'str' instead">key2="str"</warning>)