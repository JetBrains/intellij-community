def empty(shape, dtype=None, order='C'): # real signature unknown; restored from __doc__
    """
    empty(shape, dtype=float, order='C')

        Return a new array of given shape and type, without initializing entries.

        Parameters
        ----------
        shape : int or tuple of int
            Shape of the empty array
        dtype : data-type, optional
            Desired output data-type.
        order : {'C', 'F'}, optional
            Whether to store multi-dimensional data in C (row-major) or
            Fortran (column-major) order in memory.

        Returns
        -------
        out : ndarray
            Array of uninitialized (arbitrary) data with the given
            shape, dtype, and order.

        See Also
        --------
        empty_like, zeros, ones

        Notes
        -----
        `empty`, unlike `zeros`, does not set the array values to zero,
        and may therefore be marginally faster.  On the other hand, it requires
        the user to manually set all the values in the array, and should be
        used with caution.

        Examples
        --------
        >>> np.empty([2, 2])
        array([[ -9.74499359e+001,   6.69583040e-309],
               [  2.13182611e-314,   3.06959433e-309]])         #random

        >>> np.empty([2, 2], dtype=int)
        array([[-1073741821, -1067949133],
               [  496041986,    19249760]])                     #random
    """
    pass

empty([2, 2])