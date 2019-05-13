class С:
    def __init__(self, x=None):
        if x is None:
            self.foo = {
                'A': {
                    'x': 0,
                    'y': 0,
                },
            }
        else:  # init was given the previous state
            assert isinstance(x, С)
            self.foo = {
                'A': {
                    'x': x.f<caret>oo['A']['x'],
                    'y': x.foo['A']['y'],
                },
            }