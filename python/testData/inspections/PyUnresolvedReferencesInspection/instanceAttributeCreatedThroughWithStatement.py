class Foo(object):
    def __init__(self):
        with open('scope') as self.scope:
            pass

    def get_scope(self):
        return self.scope