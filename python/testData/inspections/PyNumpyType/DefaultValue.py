def lstrip(a, chars=None):
    """
    For each element in `a`, return a copy with the leading characters
    removed.

    Calls `str.lstrip` element-wise.

    Parameters
    ----------
    a : array-like, {str, unicode}
        Input array.

    chars : {str, unicode}, optional
        The `chars` argument is a string specifying the set of
        characters to be removed. If omitted or None, the `chars`
        argument defaults to removing whitespace. The `chars` argument
        is not a prefix; rather, all combinations of its values are
        stripped.

    Returns
    -------
    out : ndarray, {str, unicode}
        Output array of str or unicode, depending on input type


    """
    a_arr = numpy.asarray(a)
    return _vec_string(a_arr, a_arr.dtype, 'lstrip', (chars,))

c = np.array(['aAaAaA', '  aA  ', 'abBABba'])
lstrip(c, None)