import base64
import pprint
import sys
import unittest

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


def patch_unittest_diff():
    """
    Patches "assertEquals" to throw DiffError
    """
    if sys.version_info < (2, 7):
        return
    old = unittest.TestCase.assertEqual

    def _patched_equals(self, first, second, msg=None):
        if first != second:
            error = EqualsAssertionError(first, second, msg)
            if error.is_too_big():
                old(self, first, second, msg)
            else:
                raise error

    unittest.TestCase.assertEqual = _patched_equals


def _format_and_convert(val):
    # No need to pretty-print primitives
    return val if any(x for x in _PRIMITIVES if isinstance(val, x)) else pprint.pformat(val)


class EqualsAssertionError(AssertionError):

    def __init__(self, expected, actual, msg=None, preformated=False):
        super(AssertionError, self).__init__()
        self.expected = expected
        self.actual = actual
        self.msg = msg

        if not preformated:
            self.expected = _format_and_convert(self.expected)
            self.actual = _format_and_convert(self.actual)
            self.msg = msg if msg else ""

        self.expected = _STR_F(self.expected)
        self.actual = _STR_F(self.actual)

    def is_too_big(self):
        return len(self.actual) + len(self.expected) > 10000

    def __str__(self):
        return self._serialize()

    def __unicode__(self):
        return self._serialize()

    def _serialize(self):
        def fix_type(msg):
            return msg if _PY2K else bytes(str(msg), "utf-8")

        encoded_fields = [base64.b64encode(fix_type(x)) for x in [self.expected, self.actual, self.msg]]
        if not _PY2K:
            encoded_fields = [bytes.decode(x) for x in encoded_fields]
        return "|".join(encoded_fields)


def deserialize_error(serialized_message):
    parts = [base64.b64decode(x) for x in str(serialized_message).split("|")]
    if not _PY2K:
        parts = [bytes.decode(x) for x in parts]
    return EqualsAssertionError(parts[0], parts[1], parts[2], preformated=True)
