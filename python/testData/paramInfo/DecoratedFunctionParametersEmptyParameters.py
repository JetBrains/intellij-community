def deco(func):
    def wrapper():
        func()

    return wrapper


@deco
def bar():
    pass



bar(<arg1>)