def MyClass():
    def __init__(self):  # init is necessary to check resolve target
        pass


def my_function():
    res = MyClass()  # should not be renamed
    return res


res = MyClass()  # should not be renamed
x = res