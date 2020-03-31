def foo(bar1=22, **kwargs):
    a = bar1
    b = bar1

    c = kwargs.get("bar2", 22)
    d = kwargs.get("bar2", default=23)