my_global_bool: bool = True
my_global_list = []


def func():
    my_nonlocal_bool: bool = True
    my_nonlocal_list = []
    my_nonlocal_list.append(1)
    
    global my_global_bool
    global my_global_list
    my_global_bool.<warning descr="Unresolved attribute reference 'append' for class 'bool'">append</warning>()
    my_global_list.append(1)

    def inner():
        nonlocal my_nonlocal_bool
        nonlocal my_nonlocal_list
        my_nonlocal_bool.<warning descr="Unresolved attribute reference 'append' for class 'bool'">append</warning>()
        my_nonlocal_list.append(1)
