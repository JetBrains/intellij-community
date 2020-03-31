def foo(**kwargs):
    a = kwargs.get("bar1", 22)
    b = kwargs.get("bar1", default=23)

    c = kwa<caret>rgs.get("bar2", 22)
    d = kwargs.get("bar2", default=22)