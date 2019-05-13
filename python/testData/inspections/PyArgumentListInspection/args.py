# PY-5057
def test_iterable_not_sequence():
    print os.path.join(*xrange(10)) #pass


# PY-4968
def test_union_type():
    def foo(*args):
        pass
    bar = (1,) if True else (1, 2)
    foo(*bar) #pass


# PY-4890
def test_old_style_iterable():
    class C:
        def __getitem__(self, key):
            if 0 <= key < 10:
                return key
            else:
                raise IndexError('index out of range')
    def foo(*args):
        pass
    xs = C()
    foo(*xs) #pass
    foo(*'bar') #pass


# PY-6108
def test_unexpected_args_without_self():
    class C:
        def sm(*args, **kwargs):
            return args, kwargs
    x = C()
    x.sm(1, 2, 3) #pass
