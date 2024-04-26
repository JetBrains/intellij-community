class C:
    @classmethod
    def current(cls):
        cls.attr = cls.attr + 1
        #                <ref>
    
    @classmethod
    def next(cls):
        cls.attr = 2