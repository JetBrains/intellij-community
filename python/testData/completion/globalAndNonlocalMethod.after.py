my_global = []


def func():
    global my_global
    my_nonlocal = []
    my_global.insert()

    def inner():
        nonlocal my_nonlocal
        my_nonlocal.insert()
