def foo():
    s = "some text {param1} " \
        "some more text 12 {<warning descr="Unresolved reference 'param2'">param2</warning>}".format(param1="Val1")