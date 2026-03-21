def outer():
    def inner():
        print(1)
        print(2)
    return inner