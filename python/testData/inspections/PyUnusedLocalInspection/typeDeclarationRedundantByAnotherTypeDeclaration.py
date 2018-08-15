def func(param):
    <weak_warning descr="Type declaration for 'x' is not used">x</weak_warning>: int
    if param:
        x: str
        x = param
        print(x)
