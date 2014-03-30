import sys


class SuperClass(object):
    COPYRIGHT = sys.copyright

    def __init__(self):
        self.version = sys.api_version