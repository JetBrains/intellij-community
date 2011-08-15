class Eggs(object):
    def __enter__(self):
        return u'foo'

    def __exit__(self, exc_type, exc_val, exc_tb):
        pass

class Spam(Eggs):
    pass

def f():
    with Spam() as spam:
        spam.enc<caret>
