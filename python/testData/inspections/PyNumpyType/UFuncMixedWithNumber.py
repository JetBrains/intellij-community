def greater(x1, x2, out=None): # real signature unknown; restored from __doc__
    """
    greater(x1, x2[, out])

    Return the truth value of (x1 > x2) element-wise.

    Parameters
    ----------
    x1, x2 : array_like
        Input arrays.  If ``x1.shape != x2.shape``, they must be
        broadcastable to a common shape (which may be the shape of one or
        the other).

    Returns
    -------
    out : bool or ndarray of bool
        Array of bools, or a single bool if `x1` and `x2` are scalars.


    See Also
    --------
    greater_equal, less, less_equal, equal, not_equal

    Examples
    --------
    >>> np.greater([4,2],[2,2])
    array([ True, False], dtype=bool)

    If the inputs are ndarrays, then np.greater is equivalent to '>'.

    >>> a = np.array([4,2])
    >>> b = np.array([2,2])
    >>> a > b
    array([ True, False], dtype=bool)
    """
    pass

a = [1,2,3]
greater(a, 1)
