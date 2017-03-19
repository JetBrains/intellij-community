def test_date():
    from datetime import date, timedelta

    d = date(2000, 12, 15)

    assert d.min == date(1, 1, 1)
    assert d.max == date(9999, 12, 31)
    assert d.resolution == timedelta(1)

    assert date.fromtimestamp(2048) == date(1970, 1, 1)
    assert isinstance(date.today(), date)
    assert date.fromordinal(2048) == date(6, 8, 10)

    assert d.year == 2000
    assert d.month == 12
    assert d.day == 15

    assert d.ctime() == "Fri Dec 15 00:00:00 2000"
    assert d.strftime("%d") == "15"
    assert format(d, "%d") == "15"
    assert d.isoformat() == "2000-12-15"
    assert [d.timetuple()[i] for i in range(3)] == [2000, 12, 15]
    assert d.toordinal() == 730469

    assert d.replace() == date(2000, 12, 15)
    assert d.replace(2001) == date(2001, d.month, d.day)
    assert d.replace(2001, 11) == date(2001, 11, d.day)
    assert d.replace(2001, 11, 16) == date(2001, 11, 16)

    assert d < date(2000, 12, 16)
    assert d <= date(2000, 12, 15)
    assert d > date(2000, 12, 14)
    assert d >= date(2000, 12, 15)

    assert d + timedelta(3) == date(2000, 12, 18)
    assert d - timedelta(3) == date(2000, 12, 12)
    assert d - date(2000, 6, 8) == timedelta(190)

    assert d.weekday() == 4
    assert d.isoweekday() == 5
    assert d.isocalendar() == (2000, 50, 5)


def test_time():
    from datetime import time, timedelta

    time()
    time(23)

    t1 = time(23, 25)
    t = time(23, 25, 30)
    t2 = time(23, 25, 30, 50)

    assert t.min == time()
    assert t.max == time(23, 59, 59, 999999)
    assert t.resolution == timedelta(microseconds=1)

    assert t.hour == 23
    assert t.minute == 25
    assert t.second == 30
    assert t.microsecond == 0
    assert t.tzinfo is None

    assert t < t2
    assert t <= time(23, 25, 30)
    assert t > t1
    assert t >= time(23, 25, 30)

    assert t.isoformat() == "23:25:30"
    assert t.strftime("%m") == "01"
    assert format(t, "%m") == "01"

    assert t.utcoffset() is None
    assert t.tzname() is None
    assert t.dst() is None

    assert t.replace(22) == time(22, t.minute, t.second)
    assert t.replace(22, 24) == time(22, 24, t.second)
    assert t.replace(22, 24, 29) == time(22, 24, 29)

    t3 = time(23, 25, 20, tzinfo=None)
    assert t3.tzinfo is None
    assert t3.replace(tzinfo=None).tzinfo is None


def test_timedelta():
    from datetime import timedelta

    timedelta()
    timedelta(5)
    timedelta(5, 6)
    timedelta(5, 6, 7)
    timedelta(5, 6, 7, 8)
    timedelta(5, 6, 7, 8, 9)
    timedelta(5, 6, 7, 8, 9, 10)
    timedelta(5, 6, 7, 8, 9, 10, 11)

    td1 = timedelta(hours=1, minutes=10, seconds=10)
    td = timedelta(hours=1, minutes=10, seconds=20)
    td2 = timedelta(hours=1, minutes=10, seconds=30)

    assert td.min == timedelta(days=-999999999)
    assert td.max == timedelta(days=999999999, hours=23, minutes=59, seconds=59, microseconds=999999)
    assert td.resolution == timedelta(microseconds=1)

    assert td.days == 0
    assert td.seconds == 4220
    assert td.microseconds == 0

    assert td.total_seconds() == 4220

    assert td + td1 == timedelta(hours=2, minutes=20, seconds=30)
    assert td2 - td == timedelta(seconds=10)

    assert -td == timedelta(days=-1, hours=22, minutes=49, seconds=40)
    assert +td == td

    assert abs(td) == td

    assert td.__mul__(2) == timedelta(hours=2, minutes=20, seconds=40)
    assert td.__rmul__(2) == timedelta(hours=2, minutes=20, seconds=40)

    assert td.__floordiv__(2) == timedelta(minutes=35, seconds=10)
    assert td.__floordiv__(timedelta(minutes=5)) == NotImplemented

    assert td.__div__(2) == timedelta(minutes=35, seconds=10)
    assert td.__div__(timedelta(minutes=5)) == NotImplemented

    assert td < td2
    assert td <= timedelta(hours=1, minutes=10, seconds=20)
    assert td > td1
    assert td >= timedelta(hours=1, minutes=10, seconds=20)


def test_datetime():
    from datetime import datetime, timedelta, date, time

    datetime(2000, 12, 25)
    datetime(2000, 12, 25, 23)
    datetime(2000, 12, 25, 23, 59)
    datetime(2000, 12, 25, 23, 59, 59)
    datetime(2000, 12, 25, 23, 59, 59, 60)
    dt1 = datetime(2000, 12, 24, 23, 59, 59)
    dt = datetime(2000, 12, 25, 23, 59, 59)
    dt2 = datetime(2000, 12, 26, 23, 59, 59)

    assert dt.min == datetime(1, 1, 1)
    assert dt.max == datetime(9999, 12, 31, 23, 59, 59, 999999)
    assert dt.resolution == timedelta(microseconds=1)

    assert dt.year == 2000
    assert dt.month == 12
    assert dt.day == 25
    assert dt.hour == 23
    assert dt.minute == 59
    assert dt.second == 59
    assert dt.microsecond == 0
    assert dt.tzinfo is None

    assert datetime.utcfromtimestamp(2048) == datetime(1970, 1, 1, 0, 34, 8)
    assert isinstance(datetime.today(), datetime)
    assert datetime.fromordinal(2048) == datetime(6, 8, 10)
    assert isinstance(datetime.utcnow(), datetime)
    assert datetime.combine(date(2000, 12, 25), time(23, 59, 59)) == dt
    assert datetime.strptime("2000 12 25 23 59 59", "%Y %m %d %H %M %S") == dt

    assert dt.strftime("%Y %m %d %H %M %S") == "2000 12 25 23 59 59"
    assert format(dt, "%Y %m %d %H %M %S") == "2000 12 25 23 59 59"

    assert dt.toordinal() == 730479
    assert [dt.timetuple()[i] for i in range(6)] == [2000, 12, 25, 23, 59, 59]
    assert [dt.utctimetuple()[i] for i in range(6)] == [2000, 12, 25, 23, 59, 59]
    assert dt.date() == date(2000, 12, 25)
    assert dt.time() == time(23, 59, 59)
    assert dt.timetz() == time(23, 59, 59)
    assert dt.ctime() == "Mon Dec 25 23:59:59 2000"
    assert dt.isoformat() == "2000-12-25T23:59:59"
    assert dt.utcoffset() is None
    assert dt.tzname() is None
    assert dt.dst() is None
    assert dt.weekday() == 0
    assert dt.isoweekday() == 1
    assert dt.isocalendar() == (2000, 52, 1)

    assert dt.replace(2001) == datetime(2001, dt.month, dt.day, dt.hour, dt.minute, dt.second)
    assert dt.replace(2001, 11) == datetime(2001, 11, dt.day, dt.hour, dt.minute, dt.second)
    assert dt.replace(2001, 11, 24) == datetime(2001, 11, 24, dt.hour, dt.minute, dt.second)
    assert dt.replace(2001, 11, 24, 22) == datetime(2001, 11, 24, 22, dt.minute, dt.second)
    assert dt.replace(2001, 11, 24, 22, 58) == datetime(2001, 11, 24, 22, 58, dt.second)
    assert dt.replace(2001, 11, 24, 22, 58, 58) == datetime(2001, 11, 24, 22, 58, 58)

    assert dt < dt2
    assert dt <= datetime(2000, 12, 25, 23, 59, 59)
    assert dt > dt1
    assert dt >= datetime(2000, 12, 25, 23, 59, 59)

    assert dt + timedelta(3) == datetime(2000, 12, 28, 23, 59, 59)
    assert dt - timedelta(3) == datetime(2000, 12, 22, 23, 59, 59)
    assert dt - dt1 == timedelta(1)

    assert datetime.fromtimestamp(2048, None) == datetime(1970, 1, 1, 3, 34, 8)
    assert datetime.fromtimestamp(2048) == datetime(1970, 1, 1, 3, 34, 8)

    assert isinstance(datetime.now(None), datetime)
    assert isinstance(datetime.now(), datetime)

    dt3 = datetime(2000, 12, 25, 15, 16, 17, tzinfo=None)
    assert dt3.tzinfo is None

    dt4 = dt3.replace(tzinfo=None)
    assert dt4.tzinfo is None