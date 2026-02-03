def foo():
    global <weak_warning descr="Global variable 'var' is undefined at the module level">var</weak_warning>
    var = 1


def bar():
    global <weak_warning descr="Global variable 'var' is undefined at the module level">var</weak_warning>
    var = 2