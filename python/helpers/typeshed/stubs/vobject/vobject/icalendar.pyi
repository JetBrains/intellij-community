from datetime import timedelta
from typing import Any, Tuple

from .base import Component
from .behavior import Behavior

DATENAMES: Tuple[str, ...]
RULENAMES: Tuple[str, ...]
DATESANDRULES: Tuple[str, ...]
PRODID: str
WEEKDAYS: Tuple[str, ...]
FREQUENCIES: Tuple[str, ...]
zeroDelta: timedelta
twoHours: timedelta

def toUnicode(s: str | bytes) -> str: ...
def registerTzid(tzid, tzinfo) -> None: ...
def getTzid(tzid, smart: bool = ...): ...

utc: Any  # dateutil.tz.tz.tzutc

class TimezoneComponent(Component):
    isNative: bool
    behavior: Any
    tzinfo: Any
    name: str
    useBegin: bool
    def __init__(self, tzinfo: Any | None = ..., *args, **kwds) -> None: ...
    @classmethod
    def registerTzinfo(cls, tzinfo): ...
    def gettzinfo(self): ...
    tzid: Any
    daylight: Any
    standard: Any
    def settzinfo(self, tzinfo, start: int = ..., end: int = ...): ...
    normal_attributes: Any
    @staticmethod
    def pickTzid(tzinfo, allowUTC: bool = ...): ...
    def prettyPrint(self, level, tabwidth) -> None: ...  # type: ignore

class RecurringComponent(Component):
    isNative: bool
    def __init__(self, *args, **kwds) -> None: ...
    def getrruleset(self, addRDate: bool = ...): ...
    def setrruleset(self, rruleset): ...
    rruleset: Any
    def __setattr__(self, name, value) -> None: ...

class TextBehavior(Behavior):
    base64string: str
    @classmethod
    def decode(cls, line) -> None: ...
    @classmethod
    def encode(cls, line) -> None: ...

class VCalendarComponentBehavior(Behavior):
    defaultBehavior: Any
    isComponent: bool

class RecurringBehavior(VCalendarComponentBehavior):
    hasNative: bool
    @staticmethod
    def transformToNative(obj): ...
    @staticmethod
    def transformFromNative(obj): ...
    @staticmethod
    def generateImplicitParameters(obj) -> None: ...

class DateTimeBehavior(Behavior):
    hasNative: bool
    @staticmethod
    def transformToNative(obj): ...
    @classmethod
    def transformFromNative(cls, obj): ...

class UTCDateTimeBehavior(DateTimeBehavior):
    forceUTC: bool

class DateOrDateTimeBehavior(Behavior):
    hasNative: bool
    @staticmethod
    def transformToNative(obj): ...
    @staticmethod
    def transformFromNative(obj): ...

class MultiDateBehavior(Behavior):
    hasNative: bool
    @staticmethod
    def transformToNative(obj): ...
    @staticmethod
    def transformFromNative(obj): ...

class MultiTextBehavior(Behavior):
    listSeparator: str
    @classmethod
    def decode(cls, line) -> None: ...
    @classmethod
    def encode(cls, line) -> None: ...

class SemicolonMultiTextBehavior(MultiTextBehavior):
    listSeparator: str

class VCalendar2_0(VCalendarComponentBehavior):
    name: str
    description: str
    versionString: str
    sortFirst: Any
    knownChildren: Any
    @classmethod
    def generateImplicitParameters(cls, obj) -> None: ...
    @classmethod
    def serialize(cls, obj, buf, lineLength, validate: bool = ...): ...

class VTimezone(VCalendarComponentBehavior):
    name: str
    hasNative: bool
    description: str
    sortFirst: Any
    knownChildren: Any
    @classmethod
    def validate(cls, obj, raiseException, *args): ...
    @staticmethod
    def transformToNative(obj): ...
    @staticmethod
    def transformFromNative(obj): ...

class TZID(Behavior): ...

class DaylightOrStandard(VCalendarComponentBehavior):
    hasNative: bool
    knownChildren: Any

class VEvent(RecurringBehavior):
    name: str
    sortFirst: Any
    description: str
    knownChildren: Any
    @classmethod
    def validate(cls, obj, raiseException, *args): ...

class VTodo(RecurringBehavior):
    name: str
    description: str
    knownChildren: Any
    @classmethod
    def validate(cls, obj, raiseException, *args): ...

class VJournal(RecurringBehavior):
    name: str
    knownChildren: Any

class VFreeBusy(VCalendarComponentBehavior):
    name: str
    description: str
    sortFirst: Any
    knownChildren: Any

class VAlarm(VCalendarComponentBehavior):
    name: str
    description: str
    knownChildren: Any
    @staticmethod
    def generateImplicitParameters(obj) -> None: ...
    @classmethod
    def validate(cls, obj, raiseException, *args): ...

class VAvailability(VCalendarComponentBehavior):
    name: str
    description: str
    sortFirst: Any
    knownChildren: Any
    @classmethod
    def validate(cls, obj, raiseException, *args): ...

class Available(RecurringBehavior):
    name: str
    sortFirst: Any
    description: str
    knownChildren: Any
    @classmethod
    def validate(cls, obj, raiseException, *args): ...

class Duration(Behavior):
    name: str
    hasNative: bool
    @staticmethod
    def transformToNative(obj): ...
    @staticmethod
    def transformFromNative(obj): ...

class Trigger(Behavior):
    name: str
    description: str
    hasNative: bool
    forceUTC: bool
    @staticmethod
    def transformToNative(obj): ...
    @staticmethod
    def transformFromNative(obj): ...

class PeriodBehavior(Behavior):
    hasNative: bool
    @staticmethod
    def transformToNative(obj): ...
    @classmethod
    def transformFromNative(cls, obj): ...

class FreeBusy(PeriodBehavior):
    name: str
    forceUTC: bool

class RRule(Behavior): ...

utcDateTimeList: Any
dateTimeOrDateList: Any
textList: Any

def numToDigits(num, places): ...
def timedeltaToString(delta): ...
def timeToString(dateOrDateTime): ...
def dateToString(date): ...
def dateTimeToString(dateTime, convertToUTC: bool = ...): ...
def deltaToOffset(delta): ...
def periodToString(period, convertToUTC: bool = ...): ...
def isDuration(s): ...
def stringToDate(s): ...
def stringToDateTime(s, tzinfo: Any | None = ...): ...

escapableCharList: str

def stringToTextValues(s, listSeparator: str = ..., charList: Any | None = ..., strict: bool = ...): ...
def stringToDurations(s, strict: bool = ...): ...
def parseDtstart(contentline, allowSignatureMismatch: bool = ...): ...
def stringToPeriod(s, tzinfo: Any | None = ...): ...
def getTransition(transitionTo, year, tzinfo): ...
def tzinfo_eq(tzinfo1, tzinfo2, startYear: int = ..., endYear: int = ...): ...
