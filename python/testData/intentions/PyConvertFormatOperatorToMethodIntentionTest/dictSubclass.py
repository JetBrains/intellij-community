class MyDict(dict):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        

"%(a)s" <caret>% MyDict(a=1)