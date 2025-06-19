def outer1():
    s = 1

    def inner():
        global s
        s = "aba"

    inner()

def outer2():
    print(s)
    #     <ref>