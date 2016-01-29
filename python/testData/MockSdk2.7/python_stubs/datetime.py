# encoding: utf-8
# module datetime
# from /Users/vlan/.virtualenvs/obraz-py2.7/lib/python2.7/lib-dynload/datetime.so
# by generator 1.137
""" Fast implementation of the datetime type. """
# no imports

# Variables with simple values

MAXYEAR = 9999

MINYEAR = 1

# no functions
# classes

class date(object):
    """ date(year, month, day) --> date object """
    def ctime(self): # real signature unknown; restored from __doc__
        """ Return ctime() style string. """
        pass

    @classmethod
    def fromordinal(cls, ordinal): # known case of datetime.date.fromordinal
        """ int -> date corresponding to a proleptic Gregorian ordinal. """
        return date(1,1,1)

    @classmethod
    def fromtimestamp(cls, timestamp): # known case of datetime.date.fromtimestamp
        """ timestamp -> local date from a POSIX timestamp (like time.time()). """
        return date(1,1,1)

    def isocalendar(self): # known case of datetime.date.isocalendar
        """ Return a 3-tuple containing ISO year, week number, and weekday. """
        return (1, 1, 1)

    def isoformat(self): # known case of datetime.date.isoformat
        """ Return string in ISO 8601 format, YYYY-MM-DD. """
        return ""

    def isoweekday(self): # known case of datetime.date.isoweekday
        """
        Return the day of the week represented by the date.
        Monday == 1 ... Sunday == 7
        """
        return 0

    def replace(self, year=None, month=None, day=None): # known case of datetime.date.replace
        """ Return date with new specified fields. """
        return date(1,1,1)

    def strftime(self, format): # known case of datetime.date.strftime
        """ format -> strftime() style string. """
        return ""

    def timetuple(self): # known case of datetime.date.timetuple
        """ Return time tuple, compatible with time.localtime(). """
        return (0, 0, 0, 0, 0, 0, 0, 0, 0)

    @classmethod
    def today(self): # known case of datetime.date.today
        """ Current date or datetime:  same as self.__class__.fromtimestamp(time.time()). """
        return date(1, 1, 1)

    def toordinal(self): # known case of datetime.date.toordinal
        """ Return proleptic Gregorian ordinal.  January 1 of year 1 is day 1. """
        return 0

    def weekday(self): # known case of datetime.date.weekday
        """
        Return the day of the week represented by the date.
        Monday == 0 ... Sunday == 6
        """
        return 0

    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __format__(self, *args, **kwargs): # real signature unknown
        """ Formats self with strftime. """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __init__(self, year, month, day): # real signature unknown; restored from __doc__
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    @staticmethod # known case of __new__
    def __new__(cls, year=None, month=None, day=None): # known case of datetime.date.__new__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __radd__(self, y): # real signature unknown; restored from __doc__
        """ x.__radd__(y) <==> y+x """
        pass

    def __reduce__(self): # real signature unknown; restored from __doc__
        """ __reduce__() -> (cls, state) """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __rsub__(self, y): # real signature unknown; restored from __doc__
        """ x.__rsub__(y) <==> y-x """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    def __sub__(self, y): # real signature unknown; restored from __doc__
        """ x.__sub__(y) <==> x-y """
        pass

    day = property(lambda self: 0)
    """:type: int"""

    month = property(lambda self: 0)
    """:type: int"""

    year = property(lambda self: 0)
    """:type: int"""


    max = None # (!) real value is ''
    min = None # (!) real value is ''
    resolution = None # (!) real value is ''


class datetime(date):
    """
    datetime(year, month, day[, hour[, minute[, second[, microsecond[,tzinfo]]]]])
    
    The year, month and day arguments are required. tzinfo may be None, or an
    instance of a tzinfo subclass. The remaining arguments may be ints or longs.
    """
    def astimezone(self, tz): # known case of datetime.datetime.astimezone
        """ tz -> convert to local time in new timezone tz """
        return datetime(1, 1, 1)

    @classmethod
    def combine(cls, date, time): # known case of datetime.datetime.combine
        """ date, time -> datetime with same date and time fields """
        return datetime(1, 1, 1)

    def ctime(self): # real signature unknown; restored from __doc__
        """ Return ctime() style string. """
        pass

    def date(self): # known case of datetime.datetime.date
        """ Return date object with same year, month and day. """
        return datetime(1, 1, 1)

    def dst(self): # real signature unknown; restored from __doc__
        """ Return self.tzinfo.dst(self). """
        pass

    @classmethod
    def fromtimestamp(cls, timestamp, tz=None): # known case of datetime.datetime.fromtimestamp
        """ timestamp[, tz] -> tz's local time from POSIX timestamp. """
        return datetime(1, 1, 1)

    def isoformat(self, sep='T'): # known case of datetime.datetime.isoformat
        """
        [sep] -> string in ISO 8601 format, YYYY-MM-DDTHH:MM:SS[.mmmmmm][+HH:MM].
        
        sep is used to separate the year from the time, and defaults to 'T'.
        """
        return ""

    @classmethod
    def now(cls, tz=None): # known case of datetime.datetime.now
        """ [tz] -> new datetime with tz's local day and time. """
        return datetime(1, 1, 1)

    def replace(self, year=None, month=None, day=None, hour=None, minute=None, second=None, microsecond=None, tzinfo=None): # known case of datetime.datetime.replace
        """ Return datetime with new specified fields. """
        return datetime(1, 1, 1)

    @classmethod
    def strptime(cls, date_string, format): # known case of datetime.datetime.strptime
        """ string, format -> new datetime parsed from a string (like time.strptime()). """
        return ""

    def time(self): # known case of datetime.datetime.time
        """ Return time object with same time but with tzinfo=None. """
        return time(0, 0)

    def timetuple(self): # known case of datetime.datetime.timetuple
        """ Return time tuple, compatible with time.localtime(). """
        return (0, 0, 0, 0, 0, 0, 0, 0, 0)

    def timetz(self): # known case of datetime.datetime.timetz
        """ Return time object with same time and tzinfo. """
        return time(0, 0)

    def tzname(self): # real signature unknown; restored from __doc__
        """ Return self.tzinfo.tzname(self). """
        pass

    @classmethod
    def utcfromtimestamp(self, timestamp): # known case of datetime.datetime.utcfromtimestamp
        """ timestamp -> UTC datetime from a POSIX timestamp (like time.time()). """
        return datetime(1, 1, 1)

    @classmethod
    def utcnow(cls): # known case of datetime.datetime.utcnow
        """ Return a new datetime representing UTC day and time. """
        return datetime(1, 1, 1)

    def utcoffset(self): # real signature unknown; restored from __doc__
        """ Return self.tzinfo.utcoffset(self). """
        pass

    def utctimetuple(self): # known case of datetime.datetime.utctimetuple
        """ Return UTC time tuple, compatible with time.localtime(). """
        return (0, 0, 0, 0, 0, 0, 0, 0, 0)

    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __init__(self, year, month, day, hour=None, minute=None, second=None, microsecond=None, tzinfo=None): # real signature unknown; restored from __doc__
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    @staticmethod # known case of __new__
    def __new__(cls, year=None, month=None, day=None, hour=None, minute=None, second=None, microsecond=None, tzinfo=None): # known case of datetime.datetime.__new__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __radd__(self, y): # real signature unknown; restored from __doc__
        """ x.__radd__(y) <==> y+x """
        pass

    def __reduce__(self): # real signature unknown; restored from __doc__
        """ __reduce__() -> (cls, state) """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __rsub__(self, y): # real signature unknown; restored from __doc__
        """ x.__rsub__(y) <==> y-x """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    def __sub__(self, y): # real signature unknown; restored from __doc__
        """ x.__sub__(y) <==> x-y """
        pass

    hour = property(lambda self: 0)
    """:type: int"""

    microsecond = property(lambda self: 0)
    """:type: int"""

    minute = property(lambda self: 0)
    """:type: int"""

    second = property(lambda self: 0)
    """:type: int"""

    tzinfo = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default


    max = None # (!) real value is ''
    min = None # (!) real value is ''
    resolution = None # (!) real value is ''


class time(object):
    """
    time([hour[, minute[, second[, microsecond[, tzinfo]]]]]) --> a time object
    
    All arguments are optional. tzinfo may be None, or an instance of
    a tzinfo subclass. The remaining arguments may be ints or longs.
    """
    def dst(self): # real signature unknown; restored from __doc__
        """ Return self.tzinfo.dst(self). """
        pass

    def isoformat(self): # known case of datetime.time.isoformat
        """ Return string in ISO 8601 format, HH:MM:SS[.mmmmmm][+HH:MM]. """
        return ""

    def replace(self, hour=None, minute=None, second=None, microsecond=None, tzinfo=None): # known case of datetime.time.replace
        """ Return time with new specified fields. """
        return time(0, 0)

    def strftime(self, format): # known case of datetime.time.strftime
        """ format -> strftime() style string. """
        return ""

    def tzname(self): # real signature unknown; restored from __doc__
        """ Return self.tzinfo.tzname(self). """
        pass

    def utcoffset(self): # real signature unknown; restored from __doc__
        """ Return self.tzinfo.utcoffset(self). """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __format__(self, *args, **kwargs): # real signature unknown
        """ Formats self with strftime. """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __init__(self, hour=None, minute=None, second=None, microsecond=None, tzinfo=None): # real signature unknown; restored from __doc__
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    @staticmethod # known case of __new__
    def __new__(cls, hour=None, minute=None, second=None, microsecond=None, tzinfo=None): # known case of datetime.time.__new__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __nonzero__(self): # real signature unknown; restored from __doc__
        """ x.__nonzero__() <==> x != 0 """
        pass

    def __reduce__(self): # real signature unknown; restored from __doc__
        """ __reduce__() -> (cls, state) """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    hour = property(lambda self: 0)
    """:type: int"""

    microsecond = property(lambda self: 0)
    """:type: int"""

    minute = property(lambda self: 0)
    """:type: int"""

    second = property(lambda self: 0)
    """:type: int"""

    tzinfo = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default


    max = None # (!) real value is ''
    min = None # (!) real value is ''
    resolution = None # (!) real value is ''


class timedelta(object):
    """ Difference between two datetime values. """
    def total_seconds(self, *args, **kwargs): # real signature unknown
        """ Total seconds in the duration. """
        pass

    def __abs__(self): # real signature unknown; restored from __doc__
        """ x.__abs__() <==> abs(x) """
        pass

    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __div__(self, y): # real signature unknown; restored from __doc__
        """ x.__div__(y) <==> x/y """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __floordiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__floordiv__(y) <==> x//y """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    def __mul__(self, y): # real signature unknown; restored from __doc__
        """ x.__mul__(y) <==> x*y """
        pass

    def __neg__(self): # real signature unknown; restored from __doc__
        """ x.__neg__() <==> -x """
        pass

    @staticmethod # known case of __new__
    def __new__(cls, days=None, seconds=None, microseconds=None, milliseconds=None, minutes=None, hours=None, weeks=None): # known case of datetime.timedelta.__new__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __nonzero__(self): # real signature unknown; restored from __doc__
        """ x.__nonzero__() <==> x != 0 """
        pass

    def __pos__(self): # real signature unknown; restored from __doc__
        """ x.__pos__() <==> +x """
        pass

    def __radd__(self, y): # real signature unknown; restored from __doc__
        """ x.__radd__(y) <==> y+x """
        pass

    def __rdiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__rdiv__(y) <==> y/x """
        pass

    def __reduce__(self): # real signature unknown; restored from __doc__
        """ __reduce__() -> (cls, state) """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __rfloordiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__rfloordiv__(y) <==> y//x """
        pass

    def __rmul__(self, y): # real signature unknown; restored from __doc__
        """ x.__rmul__(y) <==> y*x """
        pass

    def __rsub__(self, y): # real signature unknown; restored from __doc__
        """ x.__rsub__(y) <==> y-x """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    def __sub__(self, y): # real signature unknown; restored from __doc__
        """ x.__sub__(y) <==> x-y """
        pass

    days = property(lambda self: 0)
    """Number of days.

    :type: int
    """

    microseconds = property(lambda self: 0)
    """Number of microseconds (>= 0 and less than 1 second).

    :type: int
    """

    seconds = property(lambda self: 0)
    """Number of seconds (>= 0 and less than 1 day).

    :type: int
    """


    max = None # (!) real value is ''
    min = None # (!) real value is ''
    resolution = None # (!) real value is ''


class tzinfo(object):
    """ Abstract base class for time zone info objects. """
    def dst(self, date_time): # known case of datetime.tzinfo.dst
        """ datetime -> DST offset in minutes east of UTC. """
        return 0

    def fromutc(self, date_time): # known case of datetime.tzinfo.fromutc
        """ datetime in UTC -> datetime in local time. """
        return datetime(1, 1, 1)

    def tzname(self, date_time): # known case of datetime.tzinfo.tzname
        """ datetime -> string name of time zone. """
        return ""

    def utcoffset(self, date_time): # known case of datetime.tzinfo.utcoffset
        """ datetime -> minutes east of UTC (negative for west of UTC). """
        return 0

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __reduce__(self, *args, **kwargs): # real signature unknown
        """ -> (cls, state) """
        pass


# variables with complex values

datetime_CAPI = None # (!) real value is ''

