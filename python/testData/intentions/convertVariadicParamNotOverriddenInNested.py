def outer(<caret>**kwargs):
    def nested():
        print(kwargs['foo'])
    return kwargs.get('bar')