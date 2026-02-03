'doc'

class X:
    'should get a warning for returning value from __init__'
    def __init__(self):
        print 'howdy'
        return 1

class Y:
    'should get a warning for returning value from __init__'
    def __init__(self, x):
        if x == 0 :
            return 0
        if x == 1 :
            return 53
        return None

class Z:
    'should not get a warning'
    def __init__(self, x):
        return


class Q(Z):
    'd'
    def __init__(self):
        v = lambda : None
        Z.__init__(self, v)


class S(Z):
   'd'
   def __init__(self):
       Z.__init__(self,lambda x: x in ['p','f'])

