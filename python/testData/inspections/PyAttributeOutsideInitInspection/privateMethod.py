
class MyClass(object):
    def __init__(self):
        self._resetData()
        self._val  = []

    def _resetData(self):
        self._val = []  # This should not be receiving a warning. The function gets called in __init__.