def foo():
    for arg in sys.argv[1:]:
        try:
            f = open(arg, 'r')
        except IOError:
            print('cannot open', arg)
        else:
            baz(f)
            #anything else you need


def baz(f_new):
    length = len(f_new.readlines())  # <---extract something from here
    print("hi from else")