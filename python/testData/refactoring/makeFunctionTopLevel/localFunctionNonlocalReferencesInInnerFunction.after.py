def func():
    pass


def local():
    x = True
    def nested():
        nonlocal x
        x = False