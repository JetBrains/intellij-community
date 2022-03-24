# -*- coding: utf-8 -*-
# Copyright (c) 2018 the Pockets team, see AUTHORS.
# Licensed under the BSD License, see LICENSE for details.

"""A pocket full of useful datetime tools!"""

from __future__ import absolute_import, print_function

import sys
from datetime import datetime, timedelta


__all__ = [
    "ceil_datetime",
    "floor_datetime",
    "round_datetime",
    "timedelta_total_seconds",
]


if sys.version_info < (2, 7):

    def timedelta_total_seconds(td):
        """
        Python 2.6 replacement function for timedelta.total_seconds().

        Args:
            td (datetime.timedelta): A `datetime.timedelta` instance.

        Returns:
            float: The total number of seconds plus the fractional
                number of microseconds in `td`.

        """
        total_seconds = td.seconds + (td.days * 24.0 * 3600.0)
        return (td.microseconds + (total_seconds * 1.0e6)) / 1.0e6


else:

    def timedelta_total_seconds(td):
        """
        Python 2.6 replacement function for timedelta.total_seconds().

        Args:
            td (datetime.timedelta): A `datetime.timedelta` instance.

        Returns:
            float: The total number of seconds plus the fractional
                number of microseconds in `td`.

        """
        return td.total_seconds()


def ceil_datetime(dt, nearest):
    """
    Rounds the given `datetime` up to the nearest `timedelta` increment.

    Note:
        `dt.microsecond` is always set to zero and ignored.

    Args:
        dt (datetime.datetime): The `datetime` instance to ceil.
        nearest (datetime.timedelta): The `timedelta` to use as the increment.

    Returns:
        datetime.datetime: A copy of `dt` ceiled to `nearest`.

    """
    dt = dt.replace(microsecond=0)
    dt_min = datetime.min.replace(tzinfo=dt.tzinfo)
    secs = timedelta_total_seconds(dt_min - dt)
    nearest_secs = timedelta_total_seconds(nearest)
    total_delta_secs = secs % nearest_secs
    delta_days = total_delta_secs // 86400  # (60 * 60 * 24)
    delta_secs = total_delta_secs % 86400.0  # (60 * 60 * 24)
    return dt + timedelta(days=delta_days, seconds=delta_secs)


def floor_datetime(dt, nearest):
    """
    Rounds the given `datetime` down to the nearest `timedelta` increment.

    Note:
        `dt.microsecond` is always set to zero and ignored.

    Args:
        dt (datetime.datetime): The `datetime` instance to floor.
        nearest (datetime.timedelta): The `timedelta` to use as the increment.

    Returns:
        datetime.datetime: A copy of `dt` floored to `nearest`.

    """
    dt = dt.replace(microsecond=0)
    dt_min = datetime.min.replace(tzinfo=dt.tzinfo)
    secs = timedelta_total_seconds(dt - dt_min)
    nearest_secs = timedelta_total_seconds(nearest)
    total_delta_secs = secs % nearest_secs
    delta_days = total_delta_secs // 86400  # (60 * 60 * 24)
    delta_secs = total_delta_secs % 86400.0  # (60 * 60 * 24)
    return dt - timedelta(days=delta_days, seconds=delta_secs)


def round_datetime(dt, nearest):
    """
    Rounds the given `datetime` up/down to the nearest `timedelta` increment.

    Note:
        `dt.microsecond` is always set to zero and ignored.

    Args:
        dt (datetime.datetime): The `datetime` instance to round.
        nearest (datetime.timedelta): The `timedelta` to use as the increment.

    Returns:
        datetime.datetime: A copy of `dt` rounded to `nearest`.

    """
    dt = dt.replace(microsecond=0)
    dt_min = datetime.min.replace(tzinfo=dt.tzinfo)
    secs = timedelta_total_seconds(dt - dt_min)
    nearest_secs = timedelta_total_seconds(nearest)
    rounded_secs = ((secs + (nearest_secs / 2)) // nearest_secs) * nearest_secs
    total_delta_secs = rounded_secs - secs
    delta_days = total_delta_secs // 86400  # (60 * 60 * 24)
    delta_secs = total_delta_secs % 86400.0  # (60 * 60 * 24)
    return dt + timedelta(days=delta_days, seconds=delta_secs)
