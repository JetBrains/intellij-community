def foo(bar, **kwargs):
    return bar + kwargs["&"] + kwargs.get("|") + kwargs["not"]