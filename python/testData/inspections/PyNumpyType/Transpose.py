
def transpose(a, axes=None):
    """
    Permute the dimensions of an array.

    Parameters
    ----------
    a : array_like
        Input array.
    axes : list of ints, optional
        By default, reverse the dimensions, otherwise permute the axes
        according to the values given.

    Returns
    -------
    p : ndarray
        `a` with its axes permuted.  A view is returned whenever
        possible.

    See Also
    --------
    rollaxis

    Examples
    --------
    >>> x = np.arange(4).reshape((2,2))
    >>> x
    array([[0, 1],
           [2, 3]])

    >>> np.transpose(x)
    array([[0, 2],
           [1, 3]])

    >>> x = np.ones((1, 2, 3))
    >>> np.transpose(x, (1, 0, 2)).shape
    (2, 1, 3)

    """
    try:
        transpose = a.transpose
    except AttributeError:
        return _wrapit(a, 'transpose', axes)
    return transpose(axes)

x = np.ones((1, 2, 3))
a = transpose(x, (1, 0, 2)).shape