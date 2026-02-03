class SM(object):
    def my_method(): pass
    my_method = staticmethod(my_method)

    def q(self):
        self.my_method()
