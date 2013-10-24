def func(f, seq):
    """
    :param f: my param
    :type f: (unknown) -> str
    :rtype: list[str]
    """
    return [f(v) for v in seq]


def f(x):
    return int(x)


def test():
    for item in func(<warning descr="Expected type '(unknown) -> str', got '(x: unknown) -> int' instead">f</warning>, []):
        pass

    for item in func(<warning descr="Expected type '(unknown) -> str', got 'int' instead">int</warning>, []):
        pass

    for item in func(<warning descr="Expected type '(unknown) -> str', got '(x: unknown) -> int' instead">lambda x: int(x)</warning>, []):
        pass

    for item in func(lambda x: str(x), []):
        pass

    for item in func(str, []):
        pass
