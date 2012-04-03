# coding=utf-8

"""
Sample of code that use fget
"""

class Parent(object):
    """
    parent class to show a simply property
    """

    @property
    def attribute(self):
        """
        some attribute in parent
        """
        return True

class Child(Parent):
    """
    child class
    """

    @property
    def attribute(self):
        """
        do something before execute code of parent attribute
        """
        print "i'm the child"
        return Parent.attribute.fget(self)

if __name__ == '__main__':
    child = Child()
    print child.attribute
