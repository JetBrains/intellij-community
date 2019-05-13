def sqrt(x, out=None): # real signature unknown; restored from __doc__
    """
    sqrt(x[, out])

    Return the positive square-root of an array, element-wise.

    Parameters
    ----------
    x : array_like
        The values whose square-roots are required.
    out : ndarray, optional
        Alternate array object in which to put the result; if provided, it
        must have the same shape as `x`

    Returns
    -------
    y : ndarray
        An array of the same shape as `x`, containing the positive
        square-root of each element in `x`.  If any element in `x` is
        complex, a complex array is returned (and the square-roots of
        negative reals are calculated).  If all of the elements in `x`
        are real, so is `y`, with negative elements returning ``nan``.
        If `out` was provided, `y` is a reference to it.

    """
    pass


meanvalue = 1
modevalue = sqrt(2 / np.pi) * meanvalue
s = np.random.rayleigh(modevalue, 1000000)