def f(i):
    xs = []
    xs[i].<weak_warning descr="Some members of 'Union[List[Any], Any]' don't have attribute 'items'">items</weak_warning>()


def g(index):
    x = [][index]
    x['foo']
