"""Skeleton for 'math' stdlib module."""


import sys
import math


def ceil(x):
    """Return the ceiling of x as a float, the smallest integer value greater
    than or equal to x.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


if sys.version_info >= (2, 6):
    def copysign(x, y):
        """Return x with the sign of y. On a platform that supports signed
        zeros, copysign(1.0, -0.0) returns -1.0.

        :type x: numbers.Real
        :type y: numbers.Real
        :rtype: float
        """
        return 0.0


def fabs(x):
    """Return the absolute value of x.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


if sys.version_info >= (2, 6):
    def factorial(x):
        """Return x factorial.

        :type x: numbers.Integral
        :rtype: int
        """
        return 0


def floor(x):
    """Return the floor of x as a float, the largest integer value less than or
    equal to x.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


def fmod(x, y):
    """Return fmod(x, y), as defined by the platform C library.

    :type x: numbers.Real
    :type y: numbers.Real
    :rtype: float
    """
    return 0.0


def frexp(x):
    """Return the mantissa and exponent of x as the pair (m, e).

    :type x: numbers.Real
    :rtype: (float, int)
    """
    return 0.0, 0


if sys.version_info >= (2, 6):
    def fsum(iterable):
        """Return an accurate floating point sum of values in the iterable.

        :type iterable: collections.Iterable[numbers.Real]
        :rtype: float
        """
        return 0.0

    def isinf(x):
        """Check if the float x is positive or negative infinity.

        :type x: numbers.Real
        :rtype: bool
        """
        return False

    def isnan(x):
        """Check if the float x is a NaN (not a number).

        :type x: numbers.Real
        :rtype: bool
        """
        return False


def ldexp(x, i):
    """Return x * (2**i).

    :type x: numbers.Real
    :type i: numbers.Integral
    :rtype: float
    """
    return 0.0


def modf(x):
    """Return the fractional and integer parts of x.

    :type x: numbers.Real
    :rtype: (float, float)
    """
    return 0.0, 0.0


if sys.version_info >= (2, 6):
    def trunc(x):
        """Return the Real value x truncated to an Integral (usually a long
        integer).

        :type x: numbers.Real
        :rtype: int
        """
        return 0


def exp(x):
    """Return e**x.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


if sys.version_info >= (2, 7):
    def expm1(x):
        """Return e**x - 1.

        :type x: numbers.Real
        :rtype: float
        """
        return 0.0


def log(x, base=math.e):
    """With one argument, return the natural logarithm of x (to base e).

    With two arguments, return the logarithm of x to the given base, calculated
    as log(x)/log(base).

    :type x: numbers.Real
    :type base: numbers.Real
    :rtype: float
    """
    return 0.0


if sys.version_info >= (2, 6):
    def log1p(x):
        """Return the natural logarithm of 1+x (base e).

        :type x: numbers.Real
        :rtype: float
        """
        return 0.0


def log10(x):
    """Return the base-10 logarithm of x.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


def pow(x, y):
    """Return x raised to the power y.

    :type x: numbers.Real
    :type y: numbers.Real
    :rtype: float
    """
    return 0.0


def sqrt(x):
    """Return the square root of x.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


def acos(x):
    """Return the arc cosine of x, in radians.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


def asin(x):
    """Return the arc sine of x, in radians.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


def atan(x):
    """Return the arc tangent of x, in radians.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


def atan2(y, x):
    """Return atan(y / x), in radians.

    :type y: numbers.Real
    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


def cos(x):
    """Return the cosine of x radians.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


def hypot(x, y):
    """Return the Euclidean norm, sqrt(x*x + y*y).

    :type x: numbers.Real
    :type y: numbers.Real
    :rtype: float
    """
    return 0.0


def sin(x):
    """Return the sine of x radians.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


def tan(x):
    """Return the tangent of x radians.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


def degrees(x):
    """Converts angle x from radians to degrees.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


def radians(x):
    """Converts angle x from degrees to radians.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


if sys.version_info >= (2, 6):
    def acosh(x):
        """Return the inverse hyperbolic cosine of x.

        :type x: numbers.Real
        :rtype: float
        """
        return 0.0

    def asinh(x):
        """Return the inverse hyperbolic sine of x.

        :type x: numbers.Real
        :rtype: float
        """
        return 0.0

    def atanh(x):
        """Return the inverse hyperbolic tangent of x.

        :type x: numbers.Real
        :rtype: float
        """
        return 0.0


def cosh(x):
    """Return the hyperbolic cosine of x.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


def sinh(x):
    """Return the hyperbolic sine of x.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


def tanh(x):
    """Return the hyperbolic tangent of x.

    :type x: numbers.Real
    :rtype: float
    """
    return 0.0


if sys.version_info >= (2, 7):
    def erf(x):
        """Return the error function at x.

        :type x: numbers.Real
        :rtype: float
        """
        return 0.0

    def erfc(x):
        """Return the complementary error function at x.

        :type x: numbers.Real
        :rtype: float
        """
        return 0.0

    def gamma(x):
        """Return the Gamma function at x.

        :type x: numbers.Real
        :rtype: float
        """
        return 0.0

    def lgamma(x):
        """Return the natural logarithm of the absolute value of the Gamma
        function at x.

        :type x: numbers.Real
        :rtype: float
        """
        return 0.0
