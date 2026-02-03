class datetime(object):
    def date(self):
        return 'foo'


class date(object):
    def __init__(self, year, month, day):
        pass

    @classmethod
    def today(cls):
        return date(0, 0, 0)

    def strftime(self, fmt):
        return 'bar'