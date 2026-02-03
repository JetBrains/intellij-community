def outer1():
    s = "aba"

    def outer2():
        def inner1():
            nonlocal s
            #        <ref>
        def inner2():
            global s