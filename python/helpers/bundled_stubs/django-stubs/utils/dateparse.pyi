from datetime import date, time, timedelta
from datetime import datetime as builtin_datetime
from re import Pattern

date_re: Pattern[str]
time_re: Pattern[str]
datetime_re: Pattern[str]
standard_duration_re: Pattern[str]
iso8601_duration_re: Pattern[str]
postgres_interval_re: Pattern[str]

def parse_date(value: str) -> date | None: ...
def parse_time(value: str) -> time | None: ...
def parse_datetime(value: str) -> builtin_datetime | None: ...
def parse_duration(value: str) -> timedelta | None: ...
