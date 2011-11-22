# PY-5057
def test_iterable_not_sequence():
    print os.path.join(*xrange(10)) #pass