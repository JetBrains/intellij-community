from __future__ import annotations

from dateutil import relativedelta


# An illustrative example for why we re-export dateutil._common.weekday from dateutil.relativedelta in the stub
class Calendar:
    def __init__(self, week_start: relativedelta.weekday = relativedelta.MO) -> None:
        self.week_start = week_start
