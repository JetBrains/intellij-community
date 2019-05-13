
foo = 0

def outer():
    def inner():
        global fo<ref>o
        print(foo)
    inner()

outer()