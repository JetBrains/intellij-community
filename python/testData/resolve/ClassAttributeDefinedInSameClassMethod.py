class C:
    @classmethod
    def m(cls):
        cls.attr = 42
        print(cls.attr)
        #           <ref>
    