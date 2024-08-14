from __future__ import annotations

import re
from typing import List, Tuple
from typing_extensions import assert_type

from xdg.IniFile import IniFile

# The "get" method is quite complex with many overloads. Check that many forms
# are valid.
# The function definition is:
# def get(self, key, group=None, locale=False, type="string", list=False, strict=False):

# Get str
assert_type(IniFile().get("some_key"), str)
assert_type(IniFile().get("some_key", None), str)
assert_type(IniFile().get("some_key", "group"), str)
assert_type(IniFile().get("some_key", "group", False), str)
assert_type(IniFile().get("some_key", "group", True), str)
assert_type(IniFile().get("some_key", "group", True, "string"), str)
assert_type(IniFile().get("some_key", "group", True, "string", False), str)
assert_type(IniFile().get("some_key", "group", True, "string", False, False), str)
assert_type(IniFile().get("some_key", "group", True, "string", False, True), str)
# Keyword parameters
assert_type(IniFile().get("some_key", group=None), str)
assert_type(IniFile().get("some_key", group="group"), str)
assert_type(IniFile().get("some_key", locale=False), str)
assert_type(IniFile().get("some_key", locale=True), str)
assert_type(IniFile().get("some_key", strict=False), str)
assert_type(IniFile().get("some_key", strict=True), str)
assert_type(IniFile().get("some_key", group="group", locale=True, strict=True), str)
# Explicitly set type as string in keyword parameters.
assert_type(IniFile().get("some_key", type="string"), str)
assert_type(IniFile().get("some_key", group=None, type="string"), str)
assert_type(IniFile().get("some_key", group="group", type="string"), str)
assert_type(IniFile().get("some_key", locale=False, type="string"), str)
assert_type(IniFile().get("some_key", locale=True, type="string"), str)
assert_type(IniFile().get("some_key", strict=False, type="string"), str)
assert_type(IniFile().get("some_key", strict=True, type="string"), str)
assert_type(IniFile().get("some_key", group="group", locale=True, strict=True, type="string"), str)
# Explicitly set list.
assert_type(IniFile().get("some_key", list=False), str)
assert_type(IniFile().get("some_key", group=None, list=False), str)
assert_type(IniFile().get("some_key", group="group", list=False), str)
assert_type(IniFile().get("some_key", locale=False, list=False), str)
assert_type(IniFile().get("some_key", locale=True, list=False), str)
assert_type(IniFile().get("some_key", strict=False, list=False), str)
assert_type(IniFile().get("some_key", strict=True, list=False), str)
assert_type(IniFile().get("some_key", group="group", locale=True, strict=True, list=False), str)
# Explicitly set both.
assert_type(IniFile().get("some_key", list=False, type="string"), str)
assert_type(IniFile().get("some_key", group=None, list=False, type="string"), str)
assert_type(IniFile().get("some_key", group="group", list=False, type="string"), str)
assert_type(IniFile().get("some_key", locale=False, list=False, type="string"), str)
assert_type(IniFile().get("some_key", locale=True, list=False, type="string"), str)
assert_type(IniFile().get("some_key", strict=False, list=False, type="string"), str)
assert_type(IniFile().get("some_key", strict=True, list=False, type="string"), str)
assert_type(IniFile().get("some_key", group="group", locale=True, strict=True, list=False, type="string"), str)

# Get List[str]
assert_type(IniFile().get("some_key", "group", True, "string", True), List[str])
assert_type(IniFile().get("some_key", "group", True, "string", True, False), List[str])
assert_type(IniFile().get("some_key", "group", True, "string", True, True), List[str])
# Keyword parameters
assert_type(IniFile().get("some_key", list=True, group=None), List[str])
assert_type(IniFile().get("some_key", list=True, group="group"), List[str])
assert_type(IniFile().get("some_key", list=True, locale=False), List[str])
assert_type(IniFile().get("some_key", list=True, locale=True), List[str])
assert_type(IniFile().get("some_key", list=True, strict=False), List[str])
assert_type(IniFile().get("some_key", list=True, strict=True), List[str])
assert_type(IniFile().get("some_key", list=True, group="group", locale=True, strict=True), List[str])
# Explicitly set list
assert_type(IniFile().get("some_key", list=True), List[str])
assert_type(IniFile().get("some_key", group=None, list=True), List[str])
assert_type(IniFile().get("some_key", group="group", list=True), List[str])
assert_type(IniFile().get("some_key", locale=False, list=True), List[str])
assert_type(IniFile().get("some_key", locale=True, list=True), List[str])
assert_type(IniFile().get("some_key", strict=False, list=True), List[str])
assert_type(IniFile().get("some_key", strict=True, list=True), List[str])
assert_type(IniFile().get("some_key", group="group", locale=True, strict=True, list=True), List[str])
# Explicitly set both
assert_type(IniFile().get("some_key", list=True, type="string"), List[str])
assert_type(IniFile().get("some_key", group=None, list=True, type="string"), List[str])
assert_type(IniFile().get("some_key", group="group", list=True, type="string"), List[str])
assert_type(IniFile().get("some_key", locale=False, list=True, type="string"), List[str])
assert_type(IniFile().get("some_key", locale=True, list=True, type="string"), List[str])
assert_type(IniFile().get("some_key", strict=False, list=True, type="string"), List[str])
assert_type(IniFile().get("some_key", strict=True, list=True, type="string"), List[str])
assert_type(IniFile().get("some_key", group="group", locale=True, strict=True, list=True, type="string"), List[str])

# Get bool
assert_type(IniFile().get("some_key", "group", True, "boolean"), bool)
assert_type(IniFile().get("some_key", "group", True, "boolean", False), bool)
assert_type(IniFile().get("some_key", "group", True, "boolean", False, False), bool)
assert_type(IniFile().get("some_key", "group", True, "boolean", False, True), bool)
# Keyword parameters
assert_type(IniFile().get("some_key", type="boolean"), bool)
assert_type(IniFile().get("some_key", type="boolean", group=None), bool)
assert_type(IniFile().get("some_key", type="boolean", group="group"), bool)
assert_type(IniFile().get("some_key", type="boolean", locale=False), bool)
assert_type(IniFile().get("some_key", type="boolean", locale=True), bool)
assert_type(IniFile().get("some_key", type="boolean", strict=False), bool)
assert_type(IniFile().get("some_key", type="boolean", strict=True), bool)
assert_type(IniFile().get("some_key", type="boolean", group="group", locale=True, strict=True), bool)
# Explicitly set list
assert_type(IniFile().get("some_key", type="boolean", list=False), bool)
assert_type(IniFile().get("some_key", type="boolean", group=None, list=False), bool)
assert_type(IniFile().get("some_key", type="boolean", group="group", list=False), bool)
assert_type(IniFile().get("some_key", type="boolean", locale=False, list=False), bool)
assert_type(IniFile().get("some_key", type="boolean", locale=True, list=False), bool)
assert_type(IniFile().get("some_key", type="boolean", strict=False, list=False), bool)
assert_type(IniFile().get("some_key", type="boolean", strict=True, list=False), bool)
assert_type(IniFile().get("some_key", type="boolean", group="group", locale=True, strict=True, list=False), bool)

# Get List[bool]
assert_type(IniFile().get("some_key", "group", True, "boolean", True), List[bool])
assert_type(IniFile().get("some_key", "group", True, "boolean", True, False), List[bool])
assert_type(IniFile().get("some_key", "group", True, "boolean", True, True), List[bool])
# Keyword parameters
assert_type(IniFile().get("some_key", type="boolean", list=True), List[bool])
assert_type(IniFile().get("some_key", type="boolean", list=True, group=None), List[bool])
assert_type(IniFile().get("some_key", type="boolean", list=True, group="group"), List[bool])
assert_type(IniFile().get("some_key", type="boolean", list=True, locale=False), List[bool])
assert_type(IniFile().get("some_key", type="boolean", list=True, locale=True), List[bool])
assert_type(IniFile().get("some_key", type="boolean", list=True, strict=False), List[bool])
assert_type(IniFile().get("some_key", type="boolean", list=True, strict=True), List[bool])
assert_type(IniFile().get("some_key", type="boolean", list=True, group="group", locale=True, strict=True), List[bool])

# Get int
assert_type(IniFile().get("some_key", "group", True, "integer"), int)
assert_type(IniFile().get("some_key", "group", True, "integer", False), int)
assert_type(IniFile().get("some_key", "group", True, "integer", False, False), int)
assert_type(IniFile().get("some_key", "group", True, "integer", False, True), int)
# Keyword parameters
assert_type(IniFile().get("some_key", type="integer"), int)
assert_type(IniFile().get("some_key", type="integer", group=None), int)
assert_type(IniFile().get("some_key", type="integer", group="group"), int)
assert_type(IniFile().get("some_key", type="integer", locale=False), int)
assert_type(IniFile().get("some_key", type="integer", locale=True), int)
assert_type(IniFile().get("some_key", type="integer", strict=False), int)
assert_type(IniFile().get("some_key", type="integer", strict=True), int)
assert_type(IniFile().get("some_key", type="integer", group="group", locale=True, strict=True), int)
# Explicitly set list.
assert_type(IniFile().get("some_key", type="integer", list=False), int)
assert_type(IniFile().get("some_key", type="integer", group=None, list=False), int)
assert_type(IniFile().get("some_key", type="integer", group="group", list=False), int)
assert_type(IniFile().get("some_key", type="integer", locale=False, list=False), int)
assert_type(IniFile().get("some_key", type="integer", locale=True, list=False), int)
assert_type(IniFile().get("some_key", type="integer", strict=False, list=False), int)
assert_type(IniFile().get("some_key", type="integer", strict=True, list=False), int)
assert_type(IniFile().get("some_key", type="integer", group="group", locale=True, strict=True, list=False), int)

# Get List[int]
assert_type(IniFile().get("some_key", "group", True, "integer", True), List[int])
assert_type(IniFile().get("some_key", "group", True, "integer", True, False), List[int])
assert_type(IniFile().get("some_key", "group", True, "integer", True, True), List[int])
# Keyword parameters
assert_type(IniFile().get("some_key", type="integer", list=True), List[int])
assert_type(IniFile().get("some_key", type="integer", list=True, group=None), List[int])
assert_type(IniFile().get("some_key", type="integer", list=True, group="group"), List[int])
assert_type(IniFile().get("some_key", type="integer", list=True, locale=False), List[int])
assert_type(IniFile().get("some_key", type="integer", list=True, locale=True), List[int])
assert_type(IniFile().get("some_key", type="integer", list=True, strict=False), List[int])
assert_type(IniFile().get("some_key", type="integer", list=True, strict=True), List[int])
assert_type(IniFile().get("some_key", type="integer", list=True, group="group", locale=True, strict=True), List[int])

# Get float
assert_type(IniFile().get("some_key", "group", True, "numeric"), float)
assert_type(IniFile().get("some_key", "group", True, "numeric", False), float)
assert_type(IniFile().get("some_key", "group", True, "numeric", False, False), float)
assert_type(IniFile().get("some_key", "group", True, "numeric", False, True), float)
# Keyword parameters
assert_type(IniFile().get("some_key", type="numeric"), float)
assert_type(IniFile().get("some_key", type="numeric", group=None), float)
assert_type(IniFile().get("some_key", type="numeric", group="group"), float)
assert_type(IniFile().get("some_key", type="numeric", locale=False), float)
assert_type(IniFile().get("some_key", type="numeric", locale=True), float)
assert_type(IniFile().get("some_key", type="numeric", strict=False), float)
assert_type(IniFile().get("some_key", type="numeric", strict=True), float)
assert_type(IniFile().get("some_key", type="numeric", group="group", locale=True, strict=True), float)
# Explicitly set list.
assert_type(IniFile().get("some_key", type="numeric", list=False), float)
assert_type(IniFile().get("some_key", type="numeric", group=None, list=False), float)
assert_type(IniFile().get("some_key", type="numeric", group="group", list=False), float)
assert_type(IniFile().get("some_key", type="numeric", locale=False, list=False), float)
assert_type(IniFile().get("some_key", type="numeric", locale=True, list=False), float)
assert_type(IniFile().get("some_key", type="numeric", strict=False, list=False), float)
assert_type(IniFile().get("some_key", type="numeric", strict=True, list=False), float)
assert_type(IniFile().get("some_key", type="numeric", group="group", locale=True, strict=True, list=False), float)

# Get List[float]
assert_type(IniFile().get("some_key", "group", True, "numeric", True), List[float])
assert_type(IniFile().get("some_key", "group", True, "numeric", True, False), List[float])
assert_type(IniFile().get("some_key", "group", True, "numeric", True, True), List[float])
# Keyword parameters
assert_type(IniFile().get("some_key", type="numeric", list=True), List[float])
assert_type(IniFile().get("some_key", type="numeric", list=True, group=None), List[float])
assert_type(IniFile().get("some_key", type="numeric", list=True, group="group"), List[float])
assert_type(IniFile().get("some_key", type="numeric", list=True, locale=False), List[float])
assert_type(IniFile().get("some_key", type="numeric", list=True, locale=True), List[float])
assert_type(IniFile().get("some_key", type="numeric", list=True, strict=False), List[float])
assert_type(IniFile().get("some_key", type="numeric", list=True, strict=True), List[float])
assert_type(IniFile().get("some_key", type="numeric", list=True, group="group", locale=True, strict=True), List[float])

# Get regex
assert_type(IniFile().get("some_key", "group", True, "regex"), re.Pattern[str])
assert_type(IniFile().get("some_key", "group", True, "regex", False), re.Pattern[str])
assert_type(IniFile().get("some_key", "group", True, "regex", False, False), re.Pattern[str])
assert_type(IniFile().get("some_key", "group", True, "regex", False, True), re.Pattern[str])
# Keyword parameters
assert_type(IniFile().get("some_key", type="regex"), re.Pattern[str])
assert_type(IniFile().get("some_key", type="regex", group=None), re.Pattern[str])
assert_type(IniFile().get("some_key", type="regex", group="group"), re.Pattern[str])
assert_type(IniFile().get("some_key", type="regex", locale=False), re.Pattern[str])
assert_type(IniFile().get("some_key", type="regex", locale=True), re.Pattern[str])
assert_type(IniFile().get("some_key", type="regex", strict=False), re.Pattern[str])
assert_type(IniFile().get("some_key", type="regex", strict=True), re.Pattern[str])
assert_type(IniFile().get("some_key", type="regex", group="group", locale=True, strict=True), re.Pattern[str])
# Explicitly set list.
assert_type(IniFile().get("some_key", type="regex", list=False), re.Pattern[str])
assert_type(IniFile().get("some_key", type="regex", group=None, list=False), re.Pattern[str])
assert_type(IniFile().get("some_key", type="regex", group="group", list=False), re.Pattern[str])
assert_type(IniFile().get("some_key", type="regex", locale=False, list=False), re.Pattern[str])
assert_type(IniFile().get("some_key", type="regex", locale=True, list=False), re.Pattern[str])
assert_type(IniFile().get("some_key", type="regex", strict=False, list=False), re.Pattern[str])
assert_type(IniFile().get("some_key", type="regex", strict=True, list=False), re.Pattern[str])
assert_type(IniFile().get("some_key", type="regex", group="group", locale=True, strict=True, list=False), re.Pattern[str])

# Get List[regex]
assert_type(IniFile().get("some_key", "group", True, "regex", True), List[re.Pattern[str]])
assert_type(IniFile().get("some_key", "group", True, "regex", True, False), List[re.Pattern[str]])
assert_type(IniFile().get("some_key", "group", True, "regex", True, True), List[re.Pattern[str]])
# Keyword parameters
assert_type(IniFile().get("some_key", type="regex", list=True), List[re.Pattern[str]])
assert_type(IniFile().get("some_key", type="regex", list=True, group=None), List[re.Pattern[str]])
assert_type(IniFile().get("some_key", type="regex", list=True, group="group"), List[re.Pattern[str]])
assert_type(IniFile().get("some_key", type="regex", list=True, locale=False), List[re.Pattern[str]])
assert_type(IniFile().get("some_key", type="regex", list=True, locale=True), List[re.Pattern[str]])
assert_type(IniFile().get("some_key", type="regex", list=True, strict=False), List[re.Pattern[str]])
assert_type(IniFile().get("some_key", type="regex", list=True, strict=True), List[re.Pattern[str]])
assert_type(IniFile().get("some_key", type="regex", list=True, group="group", locale=True, strict=True), List[re.Pattern[str]])

# Get point
assert_type(IniFile().get("some_key", "group", True, "point"), Tuple[int, int])
assert_type(IniFile().get("some_key", "group", True, "point", False), Tuple[int, int])
assert_type(IniFile().get("some_key", "group", True, "point", False, False), Tuple[int, int])
assert_type(IniFile().get("some_key", "group", True, "point", False, True), Tuple[int, int])
# Keyword parameters
assert_type(IniFile().get("some_key", type="point"), Tuple[int, int])
assert_type(IniFile().get("some_key", type="point", group=None), Tuple[int, int])
assert_type(IniFile().get("some_key", type="point", group="group"), Tuple[int, int])
assert_type(IniFile().get("some_key", type="point", locale=False), Tuple[int, int])
assert_type(IniFile().get("some_key", type="point", locale=True), Tuple[int, int])
assert_type(IniFile().get("some_key", type="point", strict=False), Tuple[int, int])
assert_type(IniFile().get("some_key", type="point", strict=True), Tuple[int, int])
assert_type(IniFile().get("some_key", type="point", group="group", locale=True, strict=True), Tuple[int, int])
# Explicitly set list.
assert_type(IniFile().get("some_key", type="point", list=False), Tuple[int, int])
assert_type(IniFile().get("some_key", type="point", group=None, list=False), Tuple[int, int])
assert_type(IniFile().get("some_key", type="point", group="group", list=False), Tuple[int, int])
assert_type(IniFile().get("some_key", type="point", locale=False, list=False), Tuple[int, int])
assert_type(IniFile().get("some_key", type="point", locale=True, list=False), Tuple[int, int])
assert_type(IniFile().get("some_key", type="point", strict=False, list=False), Tuple[int, int])
assert_type(IniFile().get("some_key", type="point", strict=True, list=False), Tuple[int, int])
assert_type(IniFile().get("some_key", type="point", group="group", locale=True, strict=True, list=False), Tuple[int, int])

# Get List[point]
assert_type(IniFile().get("some_key", "group", True, "point", True), List[Tuple[int, int]])
assert_type(IniFile().get("some_key", "group", True, "point", True, False), List[Tuple[int, int]])
assert_type(IniFile().get("some_key", "group", True, "point", True, True), List[Tuple[int, int]])
# Keyword parameters
assert_type(IniFile().get("some_key", type="point", list=True), List[Tuple[int, int]])
assert_type(IniFile().get("some_key", type="point", list=True, group=None), List[Tuple[int, int]])
assert_type(IniFile().get("some_key", type="point", list=True, group="group"), List[Tuple[int, int]])
assert_type(IniFile().get("some_key", type="point", list=True, locale=False), List[Tuple[int, int]])
assert_type(IniFile().get("some_key", type="point", list=True, locale=True), List[Tuple[int, int]])
assert_type(IniFile().get("some_key", type="point", list=True, strict=False), List[Tuple[int, int]])
assert_type(IniFile().get("some_key", type="point", list=True, strict=True), List[Tuple[int, int]])
assert_type(IniFile().get("some_key", type="point", list=True, group="group", locale=True, strict=True), List[Tuple[int, int]])
