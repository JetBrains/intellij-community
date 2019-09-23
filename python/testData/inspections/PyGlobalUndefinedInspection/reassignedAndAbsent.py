def foo():
    global <weak_warning descr="Global variable 'bar' is undefined at the module level">bar</weak_warning>
    bar = "something"
