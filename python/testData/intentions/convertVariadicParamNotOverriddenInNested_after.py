def outer(foo, bar=None, **kwargs):
    def nested():
        print(foo)
    return bar