from __future__ import print_function
import warnings


class ClassWithDeprecatedProperty:
    @property
    def x(self):
        warnings.warn("This property is deprecated!")
        return 42


obj = ClassWithDeprecatedProperty()

warnings.warn("This warning should appear in the output.")

del globals()['__warningregistry__']

print(obj.x)
print(obj)
