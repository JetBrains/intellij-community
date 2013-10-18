import random

class A(object):
    def __init__(self, parent=None):
        self.__id = str(random.randrange(pow(2, 64) - 1))
        self.__id.capitalize()
