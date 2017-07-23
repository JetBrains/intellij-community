def foo():
    s = <warning descr="Key 'param2' has no corresponding argument">"some text {param1} " \
        "some more text 12 {param2}"</warning>.format(param1="Val1")