def foo(**kwa<caret>rgs):
    a = kwargs.get("bar1", 22)
    b = kwargs.get("bar1", default=22)

    c = kwargs.get("bar2", 22)
    d = kwargs.get("bar2", default=23)