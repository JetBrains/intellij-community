class EnumMeta(type):
    """Fake Enum metaclass."""
    @property
    def __members__(cls):
        return {}


class Enum(object, metaclass=EnumMeta):
    """Fake Enum class."""

    @DynamicClassAttribute
    def name(self):
        return self._name_

    @DynamicClassAttribute
    def value(self):
        return self._value_
