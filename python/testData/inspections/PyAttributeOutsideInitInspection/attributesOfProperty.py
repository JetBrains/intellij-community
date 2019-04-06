class MyClass(object):
    def __init__(self):
        self._attr_one = 42
        self._attr_two = 42

    @property
    def prop_one(self):
        return self._attr_one

    @prop_one.setter
    def prop(self, value):
        self._attr_one = value

    def get_prop_two(self):
        return self._attr_two

    def set_prop_two(self, value):
        self._attr_two = value

    prop_two = property(get_prop_two, set_prop_two)

    def function(self, value):
        self.prop_one = value  # no warning
        self.prop_two = value  # no warning
