"""Skeleton for 'datetime' stdlib module."""


import sys
import datetime as _datetime
from time import struct_time


class timedelta(object):
    """A timedelta object represents a duration, the difference between two
    dates or times."""

    def __init__(self, days=0, seconds=0, microseconds=0, milliseconds=0,
                 minutes=0, hours=0, weeks=0):
        """Create a timedelta object.

        :type days: numbers.Real
        :type seconds: numbers.Real
        :type microseconds: numbers.Real
        :type milliseconds: numbers.Real
        :type minutes: numbers.Real
        :type hours: numbers.Real
        :type weeks: numbers.Real
        """
        self.days = 0
        self.seconds = 0
        self.microseconds = 0

    def __add__(self, other):
        """Add timedelta, date or datetime.

        :type other: T <= _datetime.timedelta | _datetime.date | _datetime.datetime
        :rtype: T
        """
        pass

    def __radd__(self, other):
        """Add timedelta, date or datetime.

        :type other: T <= _datetime.timedelta | _datetime.date | _datetime.datetime
        :rtype: T
        """
        pass

    def __sub__(self, other):
        """Subtract timedelta, date or datetime.

        :type other: _datetime.timedelta | _datetime.date | _datetime.datetime
        :rtype: _datetime.timedelta | _datetime.date | _datetime.datetime
        """
        pass

    def __rsub__(self, other):
        """Subtract timedelta, date or datetime.

        :type other: _datetime.timedelta | _datetime.date | _datetime.datetime
        :rtype: _datetime.timedelta | _datetime.date | _datetime.datetime
        """
        pass

    def __mul__(self, other):
        """Multiply by an integer.

        :type other: numbers.Integral
        :rtype: _datetime.timedelta
        """
        return _datetime.timedelta()

    def __rmul__(self, other):
        """Multiply by an integer.

        :type other: numbers.Integral
        :rtype: _datetime.timedelta
        """
        return _datetime.timedelta()

    def __floordiv__(self, other):
        """Divide by an integer or a timedelta.

        :type other: numbers.Integral | _datetime.timedelta
        :rtype: _datetime.timedelta | int
        """
        pass

    def __div__(self, other):
        """Divide by an integer.

        :type other: numbers.Integral
        :rtype: _datetime.timedelta
        """
        pass

    def __truediv__(self, other):
        """Divide by a float or a timedelta.

        :type other: numbers.Real | _datetime.timedelta
        :rtype: _datetime.timedelta | float
        """
        pass

    if sys.version_info >= (2, 7):
        def total_seconds(self):
            """Return the total number of seconds contained in the duration.

            :rtype: int
            """
            return 0

    min = _datetime.timedelta()
    max = _datetime.timedelta()
    resoultion = _datetime.timedelta()


class date(object):
    """An idealized naive date, assuming the current Gregorian calendar always
    was, and always will be, in effect."""

    def __init__(self, year, month, day):
        """Create a date object.

        :type year: numbers.Integral
        :type month: numbers.Integral
        :type day: numbers.Integral
        """
        self.year = year
        self.month = month
        self.day = day

    @classmethod
    def today(cls):
        """Return the current local date.

        :rtype: _datetime.date
        """
        return _datetime.date(0, 0, 0)

    @classmethod
    def fromtimestamp(cls, timestamp):
        """Return the local date corresponding to the POSIX timestamp, such as
        is returned by time.time().

        :type timestamp: numbers.Real
        :rtype: _datetime.date
        """
        return _datetime.date(0, 0, 0)

    @classmethod
    def fromordinal(cls, ordinal):
        """Return the date corresponding to the proleptic Gregorian ordinal,
        where January 1 of year 1 has ordinal 1.

        :type ordinal: numbers.Integral
        :rtype: _datetime.date
        """
        return _datetime.date(0, 0, 0)

    def __add__(self, other):
        """Add timedelta.

        :type other: _datetime.timedelta
        :rtype: _datetime.date
        """
        return _datetime.date(0, 0, 0)

    def __radd__(self, other):
        """Add timedelta.

        :type other: _datetime.timedelta
        :rtype: _datetime.date
        """
        return _datetime.date(0, 0, 0)

    def __sub__(self, other):
        """Subtract date or timedelta.

        :type other: _datetime.date | _datetime.timedelta
        :rtype: _datetime.timedelta | _datetime.date
        """
        pass

    def __rsub__(self, other):
        """Subtract date.

        :type other: _datetime.date
        :rtype: _datetime.timedelta
        """
        return _datetime.timedelta()

    def replace(self, year=None, month=None, day=None):
        """Return a date with the same value, except for those parameters given
        new values by whichever keyword arguments are specified.

        :type year: numbers.Integral
        :type month: numbers.Integral
        :type day: numbers.Integral
        :rtype: _datetime.date
        """
        return _datetime.date(0, 0, 0)

    def timetuple(self):
        """Return a time.struct_time such as returned by time.localtime().

        :rtype: struct_time
        """
        return struct_time()

    def toordinal(self):
        """Return the proleptic Gregorian ordinal of the date, where January 1
        of year 1 has ordinal 1.

        :rtype: int
        """
        return 0

    def weekday(self):
        """Return the day of the week as an integer, where Monday is 0 and
        Sunday is 6.

        :rtype: int
        """
        return 0

    def isoweekday(self):
        """Return the day of the week as an integer, where Monday is 1 and
        Sunday is 7.

        :rtype: int
        """
        return 0

    def isocalendar(self):
        """Return a 3-tuple, (ISO year, ISO week number, ISO weekday).

        :rtype: (int, int, int)
        """
        return (0, 0, 0)

    def isoformat(self):
        """Return a string representing the date in ISO 8601 format,
        'YYYY-MM-DD'.

        :rtype: string
        """
        return str()

    def ctime(self):
        """Return a string representing the date.

        :rtype: string
        """
        return str()

    def strftime(self, format):
        """Return a string representing the date, controlled by an explicit
        format string.

        :type format: string
        :rtype: string
        """
        return str()

    min = _datetime.date(0, 0, 0)
    max = _datetime.date(0, 0, 0)
    resoultion = _datetime.timedelta()


class datetime(object):
    """A datetime object is a single object containing all the information from
    a date object and a time object."""

    def __init__(self, year, month, day, hour=0, minute=0, second=0,
                 microsecond=0, tzinfo=None):
        """Create a datetime object.

        :type year: numbers.Integral
        :type month: numbers.Integral
        :type day: numbers.Integral
        :type hour: numbers.Integral
        :type minute: numbers.Integral
        :type second: numbers.Integral
        :type microsecond: numbers.Integral
        :type tzinfo: _datetime.tzinfo | None
        """
        self.year = year
        self.month = month
        self.day = day
        self.hour = hour
        self.minute = minute
        self.second = second
        self.microsecond = microsecond
        self.tzinfo = tzinfo

    @classmethod
    def today(cls):
        """Return the current local datetime, with tzinfo None.

        :rtype: _datetime.datetime
        """
        return _datetime.datetime(0, 0, 0)

    @classmethod
    def now(cls, tz=None):
        """Return the current local date and time.

        :type tz: _datetime.tzinfo | None
        :rtype: _datetime.datetime
        """
        return _datetime.datetime(0, 0, 0)

    @classmethod
    def utcnow(cls):
        """Return the current UTC date and time, with tzinfo None.

        :rtype: _datetime.datetime
        """
        return _datetime.datetime(0, 0, 0)

    @classmethod
    def fromtimestamp(cls, timestamp, tz=None):
        """Return the local date and time corresponding to the POSIX timestamp,
        such as is returned by time.time().

        :type timestamp: numbers.Real
        :type tz: _datetime.tzinfo | None
        :rtype: _datetime.datetime
        """
        return _datetime.datetime(0, 0, 0)

    @classmethod
    def utcfromtimestamp(cls, timestamp):
        """Return the UTC datetime corresponding to the POSIX timestamp, with
        tzinfo None.

        :type timestamp: numbers.Real
        :rtype: _datetime.datetime
        """
        return _datetime.datetime(0, 0, 0)

    @classmethod
    def fromordinal(cls, ordinal):
        """Return the datetime corresponding to the proleptic Gregorian
        ordinal.

        :type ordinal: numbers.Integral
        :rtype: _datetime.datetime
        """
        return _datetime.datetime(0, 0, 0)

    @classmethod
    def combine(cls, date, time):
        """Return a new datetime object whose date components are equal to the
        given date object's, and whose time components and tzinfo attributes
        are equal to the given time object's.

        :type date: _datetime.date
        :type time: _datetime.time
        :rtype: _datetime.datetime
        """
        return _datetime.datetime(0, 0, 0)

    @classmethod
    def strptime(cls, date_string, format):
        """Return a datetime corresponding to date_string, parsed according to
        format.

        :type date_string: string
        :type format: string
        :rtype: _datetime.datetime
        """
        return _datetime.datetime(0, 0, 0)

    def __add__(self, other):
        """Add timedelta.

        :type other: _datetime.timedelta
        :rtype: _datetime.datetime
        """
        return _datetime.datetime(0, 0, 0)

    def __radd__(self, other):
        """Add timedelta.

        :type other: _datetime.timedelta
        :rtype: _datetime.datetime
        """
        return _datetime.datetime(0, 0, 0)

    def __sub__(self, other):
        """Subtract timedelta or datetime.

        :type other: _datetime.timedelta | _datetime.datetime
        :rtype: _datetime.datetime | _datetime.timedelta
        """
        pass

    def __rsub__(self, other):
        """Subtract datetime.

        :type other: _datetime.datetime
        :rtype: _datetime.timedelta
        """
        return _datetime.timedelta()

    def date(self):
        """Return date object with same year, month and day.

        :rtype: _datetime.date
        """
        return _datetime.date(0, 0, 0)

    def time(self):
        """Return time object with same hour, minute, second and microsecond.

        :rtype: _datetime.time
        """
        return _datetime.time()

    def timetz(self):
        """Return time object with same hour, minute, second, microsecond, and
        tzinfo attributes.

        :rtype: _datetime.time
        """
        return _datetime.time()

    def replace(self, year=None, month=None, day=None, hour=None, minute=None,
                second=None, microsecond=None, tzinfo=None):
        """Return a datetime with the same attributes, except for those
        attributes given new values by whichever keyword arguments are
        specified.

        :type year: numbers.Integral
        :type month: numbers.Integral
        :type day: numbers.Integral
        :type hour: numbers.Integral
        :type minute: numbers.Integral
        :type second: numbers.Integral
        :type microsecond: numbers.Integral
        :type tzinfo: _datetime.tzinfo | None
        :rtype: _datetime.datetime
        """
        return _datetime.datetime(0, 0, 0)

    def astimezone(self, tz):
        """Return a datetime object with new tzinfo attribute tz, adjusting the
        date and time data so the result is the same UTC time as self, but in
        tz's local time.

        :type tz: _datetime.tzinfo
        :rtype: _datetime.datetime
        """
        return _datetime.datetime(0, 0, 0)

    def utcoffset(self):
        """If tzinfo is None, returns None, else returns
        self.tzinfo.utcoffset(self).

        :rtype: _datetime.timedelta | None
        """
        return _datetime.timedelta()

    def dst(self):
        """If tzinfo is None, returns None, else returns self.tzinfo.dst(self).

        :rtype: _datetime.timedelta | None
        """
        return _datetime.timedelta()

    def tzname(self):
        """If tzinfo is None, returns None, else returns
        self.tzinfo.tzname(self).

        :rtype: string | None
        """
        return str()

    def timetuple(self):
        """Return a time.struct_time such as returned by time.localtime().

        :rtype: struct_time
        """
        return struct_time()

    def utctimetuple(self):
        """If datetime instance d is naive, this is the same as d.timetuple()
        except that tm_isdst is forced to 0 regardless of what d.dst() returns.

        :rtype: struct_time
        """
        return struct_time()

    def toordinal(self):
        """Return the proleptic Gregorian ordinal of the date.

        :rtype: int
        """
        return 0

    def weekday(self):
        """Return the day of the week as an integer, where Monday is 0 and
        Sunday is 6.

        :rtype: int
        """
        return 0

    def isoweekday(self):
        """Return the day of the week as an integer, where Monday is 1 and
        Sunday is 7.

        :rtype: int
        """
        return 0

    def isocalendar(self):
        """Return a 3-tuple, (ISO year, ISO week number, ISO weekday).

        :rtype: (int, int, int)
        """
        return (0, 0, 0)

    def isoformat(self, sep='T'):
        """Return a string representing the date and time in ISO 8601 format.

        :type sep: string
        :rtype: string
        """
        return str()

    def ctime(self):
        """Return a string representing the date and time.

        :rtype: string
        """
        return str()

    def strftime(self, format):
        """Return a string representing the date and time, controlled by an
        explicit format string.

        :type format: string
        :rtype: string
        """
        return str()

    min = _datetime.datetime(0, 0, 0)
    max = _datetime.datetime(0, 0, 0)
    resoultion = _datetime.timedelta()


class time(object):
    """A time object represents a (local) time of day, independent of any
    particular day, and subject to adjustment via a tzinfo object."""

    def __init__(self, hour=0, minute=0, second=0, microsecond=0, tzinfo=None):
        """Create a time object.

        :type hour: numbers.Integral
        :type minute: numbers.Integral
        :type second: numbers.Integral
        :type microsecond: numbers.Integral
        :type tzinfo: _datetime.tzinfo | None
        """
        self.hour = hour
        self.minute = minute
        self.second = second
        self.microsecond = microsecond
        sefl.tzinfo = tzinfo

    def replace(self, hour=None, minute=None, second=None, microsecond=None,
                tzinfo=None):
        """Return a time with the same value, except for those attributes given
        new values by whichever keyword arguments are specified.

        :type hour: numbers.Integral
        :type minute: numbers.Integral
        :type second: numbers.Integral
        :type microsecond: numbers.Integral
        :type tzinfo: _datetime.tzinfo | None
        :rtype: _datetime.time
        """
        return _datetime.time()

    def isoformat(self):
        """Return a string representing the time in ISO 8601 format.

        :rtype: string
        """
        return str()

    def strftime(self, format):
        """Return a string representing the time, controlled by an explicit
        format string.

        :type format: string
        :rtype: string
        """
        return str()

    def utcoffset(self):
        """If tzinfo is None, returns None, else returns
        self.tzinfo.utcoffset(self).

        :rtype: _datetime.timedelta | None
        """
        return _datetime.timedelta()

    def dst(self):
        """If tzinfo is None, returns None, else returns self.tzinfo.dst(self).

        :rtype: _datetime.timedelta | None
        """
        return _datetime.timedelta()

    def tzname(self):
        """If tzinfo is None, returns None, else returns
        self.tzinfo.tzname(self).

        :rtype: string | None
        """
        return str()

    min = _datetime.time()
    max = _datetime.time()
    resoultion = _datetime.timedelta()
