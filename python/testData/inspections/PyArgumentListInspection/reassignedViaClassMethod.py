class Spam(object):

    def spam(cls):
        pass

    spam.eggs = False
    spam = classmethod(spam)


Spam.spam()