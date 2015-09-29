import sys
from sys import argv

class SuperClass(object):
    def __init__(self):
        pass

    def this_should_be_in_super(self, some_argument):
        if not self.args:
            self.args = argv
        self.argument = some_argument
        print(sys.api_version)
