import six

class MyType(type):
    pass

class Parent(object):
    def parent(self):
        pass


class Child(six.with_metaclass(MyType, Parent)):
    pass


Child().paren<caret>