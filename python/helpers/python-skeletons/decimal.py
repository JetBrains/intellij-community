"""Skeleton for 'decimal' stdlib module."""


import decimal


def getcontext():
    """Returns this thread's context.

    :rtype: decimal.Context
    """
    return decimal.Context()


def setcontext(context):
    """Set this thread's context to context.

    :type context: decimal.Context
    :rtype: None
    """
    pass


class Decimal(object):
    """Floating point class for decimal arithmetic."""

    def __add__(self, other, context=None):
        """Returns self + other.

        :type other: numbers.Number
        :type context: decimal.Context | None
        :rtype: decimal.Decimal
        """
        return decimal.Decimal()

    def __sub__(self, other, context=None):
        """Return self - other.

        :type other: numbers.Number
        :type context: decimal.Context | None
        :rtype: decimal.Decimal
        """
        return decimal.Decimal()

    def __mul__(self, other, context=None):
        """Return self * other.

        :type other: numbers.Number
        :type context: decimal.Context | None
        :rtype: decimal.Decimal
        """
        return decimal.Decimal()


    def __truediv__(self, other, context=None):
        """Return self / other.

        :type other: numbers.Number
        :type context: decimal.Context | None
        :rtype: decimal.Decimal
        """
        return decimal.Decimal()


    def __floordiv__(self, other, context=None):
        """Return self // other.

        :type other: numbers.Number
        :type context: decimal.Context | None
        :rtype: decimal.Decimal
        """
        return decimal.Decimal()

    def __pow__(self, other, modulo=None, context=None):
        """Return self ** other [ % modulo].

        :type other: numbers.Number
        :type modulo: numbers.Number
        :type context: decimal.Context | None
        :rtype: decimal.Decimal
        """
        return decimal.Decimal()
