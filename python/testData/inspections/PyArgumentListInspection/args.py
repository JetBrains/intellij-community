# PY-5057
def test_iterable_not_sequence():
    print os.path.join(*xrange(10)) #pass


# PY-4968
def test_union_type():
    def foo(*args):
        pass
    bar = (1,) if True else (1, 2)
    foo(*bar) #pass