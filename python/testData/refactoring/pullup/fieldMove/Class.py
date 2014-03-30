import sys
from SuperClass import SuperClass

class AnyClass(SuperClass):
    COPYRIGHT = sys.copyright

    def __init__(self):
        super().__init__()
        self.version = sys.api_version