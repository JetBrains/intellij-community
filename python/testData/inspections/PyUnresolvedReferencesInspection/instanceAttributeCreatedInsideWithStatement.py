class Foo(object):
    def __init__(self):
        with open('b.py'):
            self.scope = "a"
            pass

    def get_scope(self):
        return self.scope