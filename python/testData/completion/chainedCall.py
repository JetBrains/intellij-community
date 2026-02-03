class X(object):
    def testChain(self):
        return X()

def g():
    return X()

g().testChain().te<caret>

