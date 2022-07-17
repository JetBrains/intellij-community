import os
import pprint
import sys
import unittest

from .messages import text_type

_PY2K = sys.version_info < (3,)
_PRIMITIVES = [int, str, bool]
if _PY2K:
    # Not available in py3
    # noinspection PyUnresolvedReferences
    _PRIMITIVES.append(unicode)  # noqa
    # Not available in py3
    # noinspection PyUnresolvedReferences
    _STR_F = unicode  # noqa
else:
    _STR_F = str


def patch_unittest_diff(test_filter=None):
    """
    Patches "assertEquals" to throw DiffError.

    @:param test_filter callback to check each test. If not None it should return True to test or EqualsAssertionError will be skipped
    """
    if sys.version_info < (2, 7):
        return
    old = unittest.TestCase.assertEqual

    def _patched_equals(self, first, second, msg=None):
        try:
            old(self, first, second, msg)
            return
        except AssertionError as e:
            if not test_filter or test_filter(self):
                error = EqualsAssertionError(first, second, msg, real_exception=e)
                if error.can_be_serialized():
                    from .jb_local_exc_store import store_exception
                    store_exception(error)
            raise

    unittest.TestCase.assertEqual = _patched_equals


def _format_and_convert(val):
    if "_JB_PPRINT_PRIMITIVES" in os.environ:
        return pprint.pformat(val)
    # No need to pretty-print primitives
    return val if any(x for x in _PRIMITIVES if isinstance(val, x)) else pprint.pformat(val)


class EqualsAssertionError(AssertionError):
    MESSAGE_SEP = " :: "
    NOT_EQ_SEP = " != "

    # Real exception could be provided, but not serialized
    def __init__(self, expected, actual, msg=None, preformated=False, real_exception=None):
        self.real_exception = real_exception
        self.expected = expected
        self.actual = actual
        self.msg = text_type(msg)

        if not preformated:
            self.expected = _format_and_convert(self.expected)
            self.actual = _format_and_convert(self.actual)
            self.msg = text_type(msg) if msg else ""

        self.expected = _STR_F(self.expected)
        self.actual = _STR_F(self.actual)

    def can_be_serialized(self):
        if any([self.MESSAGE_SEP in s or self.NOT_EQ_SEP in s for s in [self.expected, self.actual, self.msg]]):
            return False
        return len(self.actual) + len(self.expected) < 10000

    def __str__(self):
        return self._serialize()

    def __unicode__(self):
        return self._serialize()

    def _serialize(self):
        return self.msg + self.MESSAGE_SEP + self.expected + self.NOT_EQ_SEP + self.actual

    @classmethod
    def deserialize_error(cls, serialized_message):
        message, diff = serialized_message.split(cls.MESSAGE_SEP)
        exp, act = diff.split(cls.NOT_EQ_SEP)
        return EqualsAssertionError(exp, act, message, preformated=True)


def deserialize_error(serialized_message):
    return EqualsAssertionError.deserialize_error(serialized_message)
