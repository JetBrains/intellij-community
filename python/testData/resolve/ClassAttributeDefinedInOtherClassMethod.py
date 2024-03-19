class C:
    @classmethod
    def usage(cls):
        print(cls.attr)
        #          <ref>
        
    @classmethod
    def first(cls):
        cls.attr = 1
        
    @classmethod
    def second(cls):
        cls.attr = 2