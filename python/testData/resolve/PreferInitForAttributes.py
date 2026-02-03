class Foo:
    def __init__(self):
        self.xyzzy = 1

    def bar(self):
        if self.xyzzy:
            self.xyzzy = None
        else:
            self.xyzzy += 1

    def baz(self):
        print(self.xyzzy)
#                   <ref>