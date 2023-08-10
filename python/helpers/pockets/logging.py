# -*- coding: utf-8 -*-
# Copyright (c) 2018 the Pockets team, see AUTHORS.
# Licensed under the BSD License, see LICENSE for details.

"""
A pocket full of useful logging tools!

The `pockets.logging` module adds a `logging.TRACE` level and a
`logging.Logger.trace` method, so messages can be logged at a lower priority
level than `logging.DEBUG`.
"""

from __future__ import absolute_import, print_function

import inspect
import logging
import logging.config
import sys
from functools import wraps

from six import text_type as unicode_type


__all__ = [
    "log_exceptions",
    "AutoLogger",
    "EagerFormattingAdapter",
    "IndentMultilineLogFormatter",
]


TRACE = 5
logging.addLevelName(TRACE, "TRACE")
logging.TRACE = TRACE


def trace(self, message, *args, **kwargs):
    # Yes, _log() takes '*args' as 'args'.
    self._log(TRACE, message, args, **kwargs)


logging.Logger.trace = trace


def log_exceptions(fn):
    """
    Decorator that wraps a function and logs any raised exceptions.

    The exception will still be raised after being logged. Also logs the
    arguments to every call at the trace level.
    """
    from pockets.autolog import log

    @wraps(fn)
    def wrapper(*args, **kwargs):
        try:
            a = [str(x)[:255] for x in args]
            kw = dict([(k[:255], str(v)[:255]) for k, v in kwargs.items()])
            log.trace("Calling %s.%s %r %r", fn.__module__, fn.__name__, a, kw)
            return fn(*args, **kwargs)
        except Exception as e:
            log.error("Error calling function %s: %s" % (fn.__name__, e))
            log.exception(e)
            raise

    return wrapper


class AutoLogger(object):
    """
    A logger proxy object with all of the methods and attributes of `Logger`.

    When an attribute (e.g., "debug") is requested, inspects the stack for the
    calling module's name, and passes that name to `logging.getLogger`.

    What this means is that you can instantiate an `AutoLogger` anywhere, and
    when you call it, the log entry shows the module where you called it, not
    where it was created.

    `AutoLogger` also inspects the local variables where it is called, looking
    for `self`. If `self` exists, its classname is added to the module name.

    Args:
        adapter_class (LoggerAdapter): optional `LoggerAdapter` class to use.
        adapter_args (list): optional args to use when instantiating an
            instance of `adapter_class`.
        adapter_kwargs (dict): optional kwargs to use when instantiating an
            instance of `adapter_class`.
    """

    def __init__(
        self, adapter_class=None, adapter_args=None, adapter_kwargs=None
    ):
        if adapter_args is None:
            adapter_args = []
        if adapter_kwargs is None:
            adapter_kwargs = {}

        self.adapter_class = adapter_class
        self.adapter_args = adapter_args
        self.adapter_kwargs = adapter_kwargs

    def __getattr__(self, name):
        f_locals = inspect.currentframe().f_back.f_locals
        if "self" in f_locals and f_locals["self"] is not None:
            other = f_locals["self"]
            caller_name = "%s.%s" % (
                other.__class__.__module__,
                other.__class__.__name__,
            )
        else:
            caller_name = inspect.currentframe().f_back.f_globals["__name__"]
        logger = logging.getLogger(caller_name)

        if self.adapter_class:
            logger = self.adapter_class(
                logger, *self.adapter_args, **self.adapter_kwargs
            )

        return getattr(logger, name)


class EagerFormattingAdapter(logging.LoggerAdapter):
    """
    A `LoggerAdapter` that immediately interpolates message arguments if the
    appropriate loglevel is set.

    This is useful because many log handlers generate log output on a separate
    thread, and the value of the log arguments may have changed by the time
    the handler interpolates them. This can lead to confusion when debugging
    difficult bugs, as the log output will not reflect what was actually
    happening when the log message was originally generated.

    For performance reasons, the interpolation ONLY happens if the appropriate
    loglevel is set. This prevents unnecessary string formatting on log
    messages that will just be thrown out anyway.

    Args:
        logger (Logger): The underlying Logger instance to use.
        extra (dict): Extra args, ignored by this implementation.
    """

    def __init__(self, logger, extra=None):
        """
        Initialize the adapter with a logger and a dict-like object which
        provides contextual information. This constructor signature allows
        easy stacking of LoggerAdapters, if so desired.

        You can effectively pass keyword arguments as shown in the
        following example::

            adapter = LoggerAdapter(someLogger, dict(p1=v1, p2="v2"))

        """
        self.logger = logger
        self.extra = extra

    def _eagerFormat(self, msg, level, args):
        """
        Eagerly apply log formatting if the appropriate level is enabled.

        Otherwise we just drop the log message (and return a string indicating
        that it was suppreseed).
        """
        if not hasattr(self, "isEnabledFor") or self.isEnabledFor(level):
            # Do the string formatting immediately.
            if args:
                return self._getUnterpolatedMessage(msg, args)
            else:
                return msg
        else:
            # Otherwise, just drop the message completely to avoid anything
            # going wrong in the future.  This text shoudl clue one in to
            # what's going on in the bizarre edge case where this ever does
            # show up.
            return "(log message suppressed due to insufficient log level)"

    def _getUnterpolatedMessage(self, msg, args):
        """
        Returns the formatted string, will first attempt str.format and will
        fallback to msg % args as it was originally.

        This is lifted almost wholesale from logging_unterpolation.
        """
        original_msg = msg

        try:
            msg = msg.format(*args)
        except UnicodeEncodeError:
            # This is most likely due to formatting a non-ascii string argument
            # into a bytestring, which the %-operator automatically handles
            # by casting the left side (the "msg" variable) in this context
            # to unicode. So we'll do that here
            #
            # Handle the attempt to print utf-8 encoded data, similar to
            # %-interpolation's handling of unicode formatting non-ascii
            # strings
            msg = unicode_type(msg).format(*args)

        except ValueError:
            # From PEP-3101, value errors are of the type raised by the format
            # method itself, so see if we should fall back to original
            # formatting since there was an issue
            if "%" in msg:
                msg = msg % args
            else:
                # We should NOT fall back, since there's no possible string
                # interpolation happening and we want a meaningful error
                # message
                raise

        if msg == original_msg and "%" in msg:
            # There must have been no string formatting methods used, given
            # the presence of args without a change in the msg
            if len(args) == 1 and isinstance(args[0], dict):
                # Handles cases like:
                # logging.debug("a %(a)d b %(b)s", {'a':1, 'b':2})
                msg = msg % args[0]
            else:
                # Fall back to original formatting
                msg = msg % args

        return msg

    def trace(self, msg, *args, **kwargs):
        """
        Delegate a trace call to the underlying logger, after adding
        contextual information from this adapter instance.
        """
        self.log(logging.TRACE, msg, *args, **kwargs)

    def debug(self, msg, *args, **kwargs):
        """
        Delegate a debug call to the underlying logger, after adding
        contextual information from this adapter instance.
        """
        self.log(logging.DEBUG, msg, *args, **kwargs)

    def info(self, msg, *args, **kwargs):
        """
        Delegate an info call to the underlying logger, after adding
        contextual information from this adapter instance.
        """
        self.log(logging.INFO, msg, *args, **kwargs)

    def warn(self, msg, *args, **kwargs):
        """
        Delegate a warning call to the underlying logger, after adding
        contextual information from this adapter instance.
        """
        self.log(logging.WARN, msg, *args, **kwargs)

    def warning(self, msg, *args, **kwargs):
        """
        Delegate a warning call to the underlying logger, after adding
        contextual information from this adapter instance.
        """
        self.log(logging.WARNING, msg, *args, **kwargs)

    def error(self, msg, *args, **kwargs):
        """
        Delegate an error call to the underlying logger, after adding
        contextual information from this adapter instance.
        """
        self.log(logging.ERROR, msg, *args, **kwargs)

    def exception(self, msg, *args, **kwargs):
        """
        Delegate an exception call to the underlying logger, after adding
        contextual information from this adapter instance.
        """
        kwargs["exc_info"] = 1
        self.log(logging.ERROR, msg, *args, **kwargs)

    def critical(self, msg, *args, **kwargs):
        """
        Delegate a critical call to the underlying logger, after adding
        contextual information from this adapter instance.
        """
        self.log(logging.CRITICAL, msg, *args, **kwargs)

    def fatal(self, msg, *args, **kwargs):
        """
        Delegate a fatal call to the underlying logger, after adding
        contextual information from this adapter instance.
        """
        self.log(logging.FATAL, msg, *args, **kwargs)

    def log(self, level, msg, *args, **kwargs):
        """
        Delegate a log call to the underlying logger, after adding
        contextual information from this adapter instance.
        """
        msg, kwargs = self.process(msg, kwargs)
        # We explicitly do not pass the args into the log method here, since
        # they should be "used up" by the eagerFormat method.
        self.logger.log(level, self._eagerFormat(msg, level, args), **kwargs)


class IndentMultilineLogFormatter(logging.Formatter):
    """
    Formatter which indents messages that are split across multiple lines.

    Indents all lines that start with a newline so they are easier for
    external log programs to parse.
    """

    def format(self, record):
        """
        Formats the given `LogRecord` by indenting all newlines.

        Args:
            record (LogRecord): The `LogRecord` to format.

        Returns:
            str: The formatted message with all newlines indented.
        """
        if sys.version_info < (2, 7):
            s = logging.Formatter.format(self, record)
        else:
            s = super(IndentMultilineLogFormatter, self).format(record)
        return s.rstrip("\n").replace("\n", "\n  ")
