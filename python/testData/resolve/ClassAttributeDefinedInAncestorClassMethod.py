class Super:
    @classmethod
    def create(cls):
        cls.attr = 42


class Sub(Super):
    @classmethod
    def m(cls):
        print(cls.attr)
        #          <ref>        
