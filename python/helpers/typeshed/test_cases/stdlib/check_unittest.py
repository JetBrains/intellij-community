import unittest
from datetime import datetime, timedelta
from decimal import Decimal
from fractions import Fraction

case = unittest.TestCase()

###
# Tests for assertAlmostEqual
###

case.assertAlmostEqual(1, 2.4)
case.assertAlmostEqual(2.4, 2.41)
case.assertAlmostEqual(Fraction(49, 50), Fraction(48, 50))
case.assertAlmostEqual(3.14, complex(5, 6))
case.assertAlmostEqual(datetime(1999, 1, 2), datetime(1999, 1, 2, microsecond=1), delta=timedelta(hours=1))
case.assertAlmostEqual(datetime(1999, 1, 2), datetime(1999, 1, 2, microsecond=1), None, "foo", timedelta(hours=1))
case.assertAlmostEqual(Decimal("1.1"), Decimal("1.11"))
case.assertAlmostEqual(2.4, 2.41, places=8)
case.assertAlmostEqual(2.4, 2.41, delta=0.02)
case.assertAlmostEqual(2.4, 2.41, None, "foo", 0.02)

case.assertAlmostEqual(2.4, 2.41, places=9, delta=0.02)  # type: ignore
case.assertAlmostEqual("foo", "bar")  # type: ignore
case.assertAlmostEqual(datetime(1999, 1, 2), datetime(1999, 1, 2, microsecond=1))  # type: ignore
case.assertAlmostEqual(Decimal("0.4"), Fraction(1, 2))  # type: ignore
case.assertAlmostEqual(complex(2, 3), Decimal("0.9"))  # type: ignore

###
# Tests for assertNotAlmostEqual
###

case.assertAlmostEqual(1, 2.4)
case.assertNotAlmostEqual(Fraction(49, 50), Fraction(48, 50))
case.assertAlmostEqual(3.14, complex(5, 6))
case.assertNotAlmostEqual(datetime(1999, 1, 2), datetime(1999, 1, 2, microsecond=1), delta=timedelta(hours=1))
case.assertNotAlmostEqual(datetime(1999, 1, 2), datetime(1999, 1, 2, microsecond=1), None, "foo", timedelta(hours=1))

case.assertNotAlmostEqual(2.4, 2.41, places=9, delta=0.02)  # type: ignore
case.assertNotAlmostEqual("foo", "bar")  # type: ignore
case.assertNotAlmostEqual(datetime(1999, 1, 2), datetime(1999, 1, 2, microsecond=1))  # type: ignore
case.assertNotAlmostEqual(Decimal("0.4"), Fraction(1, 2))  # type: ignore
case.assertNotAlmostEqual(complex(2, 3), Decimal("0.9"))  # type: ignore

###
# Tests for assertGreater
###


class Spam:
    def __lt__(self, other: object) -> bool:
        return True


class Eggs:
    def __gt__(self, other: object) -> bool:
        return True


class Ham:
    def __lt__(self, other: "Ham") -> bool:
        if not isinstance(other, Ham):
            return NotImplemented
        return True


class Bacon:
    def __gt__(self, other: "Bacon") -> bool:
        if not isinstance(other, Bacon):
            return NotImplemented
        return True


case.assertGreater(5.8, 3)
case.assertGreater(Decimal("4.5"), Fraction(3, 2))
case.assertGreater(Fraction(3, 2), 0.9)
case.assertGreater(Eggs(), object())
case.assertGreater(object(), Spam())
case.assertGreater(Ham(), Ham())
case.assertGreater(Bacon(), Bacon())

case.assertGreater(object(), object())  # type: ignore
case.assertGreater(datetime(1999, 1, 2), 1)  # type: ignore
case.assertGreater(Spam(), Eggs())  # type: ignore
case.assertGreater(Ham(), Bacon())  # type: ignore
case.assertGreater(Bacon(), Ham())  # type: ignore
