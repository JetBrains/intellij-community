class EmptyParent:pass

class SomeParent:
    PARENT_CLASS_FIELD = 42

    def __init__(self):
        self.parent_instance_field = "egg"

    def parent_func(self):
        pass


class ChildWithDependencies(SomeParent, EmptyParent):
    CLASS_FIELD_FOO = 42
    CLASS_FIELD_DEPENDS_ON_CLASS_FIELD_FOO = CLASS_FIELD_FOO
    CLASS_FIELD_DEPENDS_ON_PARENT_FIELD = SomeParent.PARENT_CLASS_FIELD

    def __init__(self):
        SomeParent.__init__(self)
        self.instance_field_bar = 42
        self.depends_on_instance_field_bar = self.instance_field_bar
        self.depends_on_class_field_foo = ChildWithDependencies.CLASS_FIELD_FOO

    def normal_method(self):
        pass

    def method_depends_on_parent_method(self):
        self.parent_func()
        pass

    def method_depends_on_parent_field(self):
        i = self.parent_instance_field
        pass

    def method_depends_on_normal_method(self):
        self.normal_method()

    def method_depends_on_instance_field_bar(self):
        eggs = self.instance_field_bar