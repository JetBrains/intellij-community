class DirectMeta(type):
    def __init__(cls, arg1, arg2):
        cls.a = arg1
        print cls.a
