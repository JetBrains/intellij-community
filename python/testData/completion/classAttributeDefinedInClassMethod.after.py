class C:
    @classmethod
    def first(cls):
        cls.attr = 1
        
    @classmethod
    def second(cls):
        print(cls.attr)