# enum.auto() value hints show the generated member value
from enum import Enum, Flag, StrEnum, auto


# Consecutive integers for a plain Enum
class Priority(Enum):
    LOW = auto()/*<# = 1#>*/
    MEDIUM = 5
    HIGH = auto()/*<# = 6#>*/


# Successive powers of two for a Flag
class Permission(Flag):
    READ = auto()/*<# = 1#>*/
    WRITE = auto()/*<# = 2#>*/
    EXECUTE = auto()/*<# = 4#>*/


# Lower-cased member name for a StrEnum
class Color(StrEnum):
    RED = auto()/*<# = 'red'#>*/
    GREEN = auto()/*<# = 'green'#>*/
