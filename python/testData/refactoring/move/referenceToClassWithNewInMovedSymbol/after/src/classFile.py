from collections import namedtuple


class Pipeline(namedtuple('_Pipeline', 'name')):

    def __new__(cls, name):
        return super(Pipeline, cls).__new__(cls, name)

    def __init__(self, name):
        pass
