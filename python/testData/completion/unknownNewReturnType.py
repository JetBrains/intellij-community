def foo(): return foo()

class Template(object):
    def __new__(cls):
        return foo()

    def xyzzy(self):
        pass

t = Template()
t.x<caret>
