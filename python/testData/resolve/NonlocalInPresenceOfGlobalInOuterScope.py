def outer1():
    x = 1
    def outer2():
        global x
        x = "ab"
        def inner():
            nonlocal x
            #        <ref>