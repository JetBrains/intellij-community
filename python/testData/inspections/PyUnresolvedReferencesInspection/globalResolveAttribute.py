my_global = []


def func():
    global my_global
    my_global.<warning descr="Unresolved attribute reference 'boom' for class 'list'">boom</warning>()
