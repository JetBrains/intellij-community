from sys import _version_info

class _Feature:
    def getOptionalRelease(self) -> _version_info: ...
    def getMandatoryRelease(self) -> _version_info: ...

absolute_import = ...  # type: _Feature
division = ...  # type: _Feature
generators = ...  # type: _Feature
nested_scopes = ...  # type: _Feature
print_function = ...  # type: _Feature
unicode_literals = ...  # type: _Feature
with_statement = ...  # type: _Feature
