import warnings


class ClassWithDeprecatedProperty:
    @property
    def x(self):
        warnings.warn("This property is deprecated!", DeprecationWarning)
        return 42


obj = ClassWithDeprecatedProperty()

warnings.warn("This warning should appear in the output.")

print(obj.x)
print(obj)
