def fun(*args, **kwargs):
    print(type(kwargs))
    for key in kwargs:
        print("%s = %s" % (key, kwargs[key]))


fun(name="geeks", ID="101", language="Python")