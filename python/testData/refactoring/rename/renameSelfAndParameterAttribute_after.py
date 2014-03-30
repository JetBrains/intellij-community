class С:
    def __init__(self, x=None):
        if x is None:
            self.bar = {
                'A': {
                    'x': 0,
                    'y': 0,
                },
            }
        else:  # init was given the previous state
            assert isinstance(x, С)
            self.bar = {
                'A': {
                    'x': x.bar['A']['x'],
                    'y': x.bar['A']['y'],
                },
            }