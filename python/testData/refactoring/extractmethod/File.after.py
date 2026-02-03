def __init__(self):
    for base in self__class__.__bases__:
        bar(base, self)


def bar(base_new, self_new):
    try:
        base_new.__init__(self_new)
    except AttributeError:
        pass
