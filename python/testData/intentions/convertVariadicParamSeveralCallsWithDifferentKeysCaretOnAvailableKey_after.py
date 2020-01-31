def foo(bar2=22, **kwargs):
    a = kwargs.get("bar1", 22)
    b = kwargs.get("bar1", default=23)

    c = bar2
    d = bar2