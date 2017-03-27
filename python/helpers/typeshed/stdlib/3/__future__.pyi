import sys

class _Feature:
    def getOptionalRelease(self) -> sys._version_info: ...
    def getMandatoryRelease(self) -> sys._version_info: ...

absolute_import = ...  # type: _Feature
division = ...  # type: _Feature
generators = ...  # type: _Feature
nested_scopes = ...  # type: _Feature
print_function = ...  # type: _Feature
unicode_literals = ...  # type: _Feature
with_statement = ...  # type: _Feature

if sys.version_info[:2] >= (3, 5):
    generator_stop = ...  # type: _Feature
