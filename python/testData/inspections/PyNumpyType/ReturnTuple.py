def unique(ar, return_index=False, return_inverse=False, return_counts=False):
    """
    Find the unique elements of an array.

    Returns the sorted unique elements of an array. There are two optional
    outputs in addition to the unique elements: the indices of the input array
    that give the unique values, and the indices of the unique array that
    reconstruct the input array.

    Parameters
    ----------
    ar : array_like
        Input array. This will be flattened if it is not already 1-D.
    return_index : bool, optional
        If True, also return the indices of `ar` that result in the unique
        array.
    return_inverse : bool, optional
        If True, also return the indices of the unique array that can be used
        to reconstruct `ar`.
    return_counts : bool, optional
        .. versionadded:: 1.9.0
        If True, also return the number of times each unique value comes up
        in `ar`.

    Returns
    -------
    unique : ndarray
        The sorted unique values.
    unique_indices : ndarray
        The indices of the first occurrences of the unique values in the
        (flattened) original array. Only provided if `return_index` is True.
    unique_inverse : ndarray
        The indices to reconstruct the (flattened) original array from the
        unique array. Only provided if `return_inverse` is True.
    unique_counts : ndarray
        .. versionadded:: 1.9.0
        The number of times each of the unique values comes up in the
        original array. Only provided if `return_counts` is True.

    """
    ar = np.asanyarray(ar).flatten()

a=1
u, indices = <warning descr="Too many values to unpack">unique(a, return_index=True)</warning>