def factorial(n):
    """Return the factorial of n, an exact integer >= 0.

    If the result is small enough to fit in an int, return an int.
    Else return a long.

    >>> [factorial(n) for n in range(6)]
    [1, 1, 2, 6, 24, 120]
    """

    import math
    if not n >= 0:
        raise ValueError("n must be >= 0")
    if math.floor(n) != n:
        raise ValueError("n must be exact integer")
    if n+1 == n:  # catch a value like 1e300
        raise OverflowError("n too large")
    result = 1
    factor = 2
    while factor <= n:
        result *= factor
        factor += 1
    return result

class FirstGoodTest:
  """
  >>> [factorial(n) for n in range(6)]
  [1, 1]
  """
  def test_passes(self):
    pass

class SecondGoodTest:
  def test_passes(self):
    """
    >>> [factorial(n) for n in range(6)]
    [1, 1, 2, 6]
    """
    pass