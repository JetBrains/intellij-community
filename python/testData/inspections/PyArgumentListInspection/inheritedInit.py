class Foo:
    def __init__(self, name):
        print name

class Bar(Foo):
    def __init__(self, name):
        Foo.__init__(self, name)  #ok
