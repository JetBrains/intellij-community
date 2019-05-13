class Meta(type):
    def __new__(<weak_warning descr="Usually first parameter of such methods is named 'metacls'">self</weak_warning>, *rest): # rename to "metacls"
        pass

    @classmethod
    def baz(<weak_warning descr="Usually first parameter of such methods is named 'metacls'">moo</weak_warning>): # <- rename to "metacls"
        return "foobar"