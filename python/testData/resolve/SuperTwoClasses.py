class Base1(object):
    pass

class Base2(object):
    def my_call(self):
        print 'foo'

class TheClass(Base1, Base2):

    def my_call(self): # overrides Base2.my_call
        super(TheClass, self).my_call() # can't resolve
#                                 <ref>

TheClass().my_call()
