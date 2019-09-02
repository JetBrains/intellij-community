class Test(object):
    def is_test(self):
        return hasattr(self, 'foo') and hasattr(self, 'bar') and self.<caret>