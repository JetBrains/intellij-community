class C:
    attr = "foo"
    
    @classmethod
    def create(cls):
        cls.attr = "bar"
        print(cls.attr)
        #           <ref>
    