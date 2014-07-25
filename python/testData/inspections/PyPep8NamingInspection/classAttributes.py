class Spam(object):
    CLASS_FIELD = 0

    @classmethod
    def eggs(cls):
        cls.CLASS_FIELD = 1

    @staticmethod
    def ham():
        Spam.CLASS_FIELD = 2