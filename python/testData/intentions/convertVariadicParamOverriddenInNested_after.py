def outer(bar=None, **kwargs):
    def nested(**kwargs):
        print(kwargs['foo'])
    return bar