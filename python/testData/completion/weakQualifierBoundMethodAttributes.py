class MyClass(object):
    def method(self):
        pass

if True:
    inst = MyClass()
else:
    inst = unresolved

inst.method.__<caret>