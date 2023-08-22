import logging


class SuperClass(object):
    def __init__(self):
        pass

    @property
    def new_property(self):
        return 1

    @new_property.setter
    def new_property(self, value):
        logging.debug("Setting %s", value)

    @new_property.deleter
    def new_property(self):
        pass
