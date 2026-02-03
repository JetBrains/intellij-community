def f(i):
    xs = []
    xs[i].<weak_warning descr="Member 'List[Any]' of 'Union[List[Any], Any]' does not have attribute 'items'">items</weak_warning>()


def g(index):
    x = [][index]
    x['foo']
