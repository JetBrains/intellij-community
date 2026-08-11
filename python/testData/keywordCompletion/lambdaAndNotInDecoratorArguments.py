def decorator(arg):
    def wrapper(func):
        return func

    return wrapper


@decorator(<caret>)
def foo():
    pass
