def outer(<caret>**kwargs):
    def nested(**kwargs):
        print(kwargs['foo'])
    return kwargs.get('bar')