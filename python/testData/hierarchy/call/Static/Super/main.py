class SomeBaseClass(object):
    def a_method(self):
        pass


class SubclassOne(SomeBaseClass):
    def a_method(self):
        super(SubclassOne, self).a_method()


class SubclassTwo(SomeBaseClass):
    def a_method(self):
        super(SubclassTwo, self).a_method()


def that_function():
    obj = SubclassTwo()
    return obj.a_met<caret>hod()
