def f():
    """
    Yields:
        int: meaning of life, universe and everything

    Returns:
        Generator[Literal[42], Any, None]: 
        
    Example:
        print(next(f))
    """
    yield 42
    return