class value():
    pass

class MyClass(object):
    def bar(self):
        foo = getattr(self, 'foo')
        foo()
