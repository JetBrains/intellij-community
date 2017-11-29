from lib import Alice


class Intermediary(Alice):
    pass


class Alice(Intermediary):
    def __str__(self):
        return 'New Alice'