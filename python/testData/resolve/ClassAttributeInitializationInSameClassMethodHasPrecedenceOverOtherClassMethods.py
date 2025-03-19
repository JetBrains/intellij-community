class C:
    @classmethod
    def create(cls):
        cls.attr = "foo"
    
    @classmethod
    def current(cls):
        cls.attr = "bar"
        print(cls.attr)
        #           <ref>
    