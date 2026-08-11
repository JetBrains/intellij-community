from b import ID


def func(x: ID):
    pass


func(<warning descr="Expected type 'ID', got 'Literal[42]' instead">42</warning>)
