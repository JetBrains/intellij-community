my_global = []


def func():
    global my_global
    my_nonlocal = []
    my_global.inse<caret>

    def inner():
        nonlocal my_nonlocal
        my_nonlocal.inse<caret>
