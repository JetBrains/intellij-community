"""Skeleton for 'nose.tools' module."""


import sys


def assert_equal(first, second, msg=None):
    """Fail if the two objects are unequal as determined by the '==' operator.
    """
    pass


def assert_not_equal(first, second, msg=None):
    """Fail if the two objects are equal as determined by the '==' operator.
    """
    pass


def assert_true(expr, msg=None):
    """Check that the expression is true."""
    pass


def assert_false(expr, msg=None):
    """Check that the expression is false."""
    pass


if sys.version_info >= (2, 7):
    def assert_is(expr1, expr2, msg=None):
        """Just like assert_true(a is b), but with a nicer default message."""
        pass

    def assert_is_not(expr1, expr2, msg=None):
        """Just like assert_true(a is not b), but with a nicer default message.
        """
        pass

    def assert_is_none(obj, msg=None):
        """Same as assert_true(obj is None), with a nicer default message.
        """
        pass

    def assert_is_not_none(obj, msg=None):
        """Included for symmetry with assert_is_none."""
        pass

    def assert_in(member, container, msg=None):
        """Just like assert_true(a in b), but with a nicer default message."""
        pass

    def assert_not_in(member, container, msg=None):
        """Just like assert_true(a not in b), but with a nicer default message.
        """
        pass

    def assert_is_instance(obj, cls, msg=None):
        """Same as assert_true(isinstance(obj, cls)), with a nicer default
        message.
        """
        pass

    def assert_not_is_instance(obj, cls, msg=None):
        """Included for symmetry with assert_is_instance."""
        pass


def assert_raises(excClass, callableObj=None, *args, **kwargs):
    """Fail unless an exception of class excClass is thrown by callableObj when
    invoked with arguments args and keyword arguments kwargs.

    If called with callableObj omitted or None, will return a
    context object used like this::

         with assert_raises(SomeException):
             do_something()

    :rtype: unittest.case._AssertRaisesContext | None
    """
    pass


if sys.version_info >= (2, 7):
    def assert_raises_regexp(expected_exception, expected_regexp,
                             callable_obj=None, *args, **kwargs):
        """Asserts that the message in a raised exception matches a regexp.

        :rtype: unittest.case._AssertRaisesContext | None
        """
        pass


def assert_almost_equal(first, second, places=None, msg=None, delta=None):
    """Fail if the two objects are unequal as determined by their difference
    rounded to the given number of decimal places (default 7) and comparing to
    zero, or by comparing that the between the two objects is more than the
    given delta.
    """
    pass


def assert_not_almost_equal(first, second, places=None, msg=None, delta=None):
    """Fail if the two objects are equal as determined by their difference
    rounded to the given number of decimal places (default 7) and comparing to
    zero, or by comparing that the between the two objects is less than the
    given delta.
    """
    pass


if sys.version_info >= (2, 7):
    def assert_greater(a, b, msg=None):
        """Just like assert_true(a > b), but with a nicer default message."""
        pass

    def assert_greater_equal(a, b, msg=None):
        """Just like assert_true(a >= b), but with a nicer default message."""
        pass

    def assert_less(a, b, msg=None):
        """Just like assert_true(a < b), but with a nicer default message."""
        pass

    def assert_less_equal(a, b, msg=None):
        """Just like self.assertTrue(a <= b), but with a nicer default
        message.
        """
        pass

    def assert_regexp_matches(text, expected_regexp, msg=None):
        """Fail the test unless the text matches the regular expression."""
        pass

    def assert_not_regexp_matches(text, unexpected_regexp, msg=None):
        """Fail the test if the text matches the regular expression."""
        pass

    def assert_items_equal(expected_seq, actual_seq, msg=None):
        """An unordered sequence specific comparison. It asserts that
        actual_seq and expected_seq have the same element counts.
        """
        pass

    def assert_dict_contains_subset(expected, actual, msg=None):
        """Checks whether actual is a superset of expected."""
        pass

    def assert_multi_line_equal(first, second, msg=None):
        """Assert that two multi-line strings are equal."""
        pass

    def assert_sequence_equal(seq1, seq2, msg=None, seq_type=None):
        """An equality assertion for ordered sequences (like lists and tuples).
        """
        pass

    def assert_list_equal(list1, list2, msg=None):
        """A list-specific equality assertion."""
        pass

    def assert_tuple_equal(tuple1, tuple2, msg=None):
        """A tuple-specific equality assertion."""
        pass

    def assert_set_equal(set1, set2, msg=None):
        """A set-specific equality assertion."""
        pass

    def assert_dict_equal(d1, d2, msg=None):
        """A dict-specific equality assertion."""
        pass


assert_equals = assert_equal
assert_not_equals = assert_not_equal
assert_almost_equals = assert_almost_equal
assert_not_almost_equals = assert_not_almost_equal
