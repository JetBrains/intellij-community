
class A(object):
    def target_func(self, p):
        pass

    def another_func(self):
        self.target_func()
        pass

