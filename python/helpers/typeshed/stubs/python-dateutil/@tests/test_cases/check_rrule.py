from typing import Union
from typing_extensions import assert_type

from dateutil.rrule import rrule, rruleset, rrulestr

rs1 = rrulestr("", forceset=True)
assert_type(rs1, rruleset)

rs2 = rrulestr("", compatible=True)
assert_type(rs2, rruleset)

rs3 = rrulestr("")
assert_type(rs3, Union[rrule, rruleset])
