from datetime import date


def foo(my_date):
    if isinstance(my_date, date):
        my_date.timetuple()