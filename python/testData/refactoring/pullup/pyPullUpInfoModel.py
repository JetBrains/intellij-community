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


    @property
    def new_property(self):
        return 1

    def _set_prop(self, val):
        pass

    def _get_prop(self):
        return 1

    def _del_prop(self):
        pass

    old_property = property(fset=_set_prop)
    old_property_2 = property(fget=_get_prop)
    old_property_3 = property(fdel=_del_prop)


    @property
    def new_property(self):
        return 1

    @new_property.setter
    def new_property(self, val):
        pass

    @property
    def new_property_2(self):
        return 1


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

    def method_depends_on_old_property(self):
        i = 12
        self.old_property = i
        q = self.old_property_2
        del self.old_property_3

    def method_depends_on_new_property(self):
        self.new_property = 12
        print(self.new_property_2)
