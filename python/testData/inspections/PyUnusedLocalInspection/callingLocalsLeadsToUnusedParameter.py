def outer(arg_one):
    def inner():
        print(locals())
        print(arg_one)
    return inner