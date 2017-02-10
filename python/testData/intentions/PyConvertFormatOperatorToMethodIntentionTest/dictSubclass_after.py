class MyDict(dict):
    def __init__(self, **kwargs):
        super().__init__(**kwargs)


"{a}".format(**MyDict(a=1))