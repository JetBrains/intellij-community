class Foo(object):
    def __init__(self):
        with open('scope') as self.scope:
            pass