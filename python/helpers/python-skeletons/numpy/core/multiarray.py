class ndarray(object):
    """
    ndarray(shape, dtype=float, buffer=None, offset=0,
                strides=None, order=None)

        An array object represents a multidimensional, homogeneous array
        of fixed-size items.  An associated data-type object describes the
        format of each element in the array (its byte-order, how many bytes it
        occupies in memory, whether it is an integer, a floating point number,
        or something else, etc.)

        Arrays should be constructed using `array`, `zeros` or `empty` (refer
        to the See Also section below).  The parameters given here refer to
        a low-level method (`ndarray(...)`) for instantiating an array.

        For more information, refer to the `numpy` module and examine the
        the methods and attributes of an array.

        Parameters
        ----------
        (for the __new__ method; see Notes below)

        shape : tuple of ints
            Shape of created array.
        dtype : data-type, optional
            Any object that can be interpreted as a numpy data type.
        buffer : object exposing buffer interface, optional
            Used to fill the array with data.
        offset : int, optional
            Offset of array data in buffer.
        strides : tuple of ints, optional
            Strides of data in memory.
        order : {'C', 'F'}, optional
            Row-major or column-major order.

        Attributes
        ----------
        T : ndarray
            Transpose of the array.
        data : buffer
            The array's elements, in memory.
        dtype : dtype object
            Describes the format of the elements in the array.
        flags : dict
            Dictionary containing information related to memory use, e.g.,
            'C_CONTIGUOUS', 'OWNDATA', 'WRITEABLE', etc.
        flat : numpy.flatiter object
            Flattened version of the array as an iterator.  The iterator
            allows assignments, e.g., ``x.flat = 3`` (See `ndarray.flat` for
            assignment examples; TODO).
        imag : ndarray
            Imaginary part of the array.
        real : ndarray
            Real part of the array.
        size : int
            Number of elements in the array.
        itemsize : int
            The memory use of each array element in bytes.
        nbytes : int
            The total number of bytes required to store the array data,
            i.e., ``itemsize * size``.
        ndim : int
            The array's number of dimensions.
        shape : tuple of ints
            Shape of the array.
        strides : tuple of ints
            The step-size required to move from one element to the next in
            memory. For example, a contiguous ``(3, 4)`` array of type
            ``int16`` in C-order has strides ``(8, 2)``.  This implies that
            to move from element to element in memory requires jumps of 2 bytes.
            To move from row-to-row, one needs to jump 8 bytes at a time
            (``2 * 4``).
        ctypes : ctypes object
            Class containing properties of the array needed for interaction
            with ctypes.
        base : ndarray
            If the array is a view into another array, that array is its `base`
            (unless that array is also a view).  The `base` array is where the
            array data is actually stored.

        See Also
        --------
        array : Construct an array.
        zeros : Create an array, each element of which is zero.
        empty : Create an array, but leave its allocated memory unchanged (i.e.,
                it contains "garbage").
        dtype : Create a data-type.

        Notes
        -----
        There are two modes of creating an array using ``__new__``:

        1. If `buffer` is None, then only `shape`, `dtype`, and `order`
           are used.
        2. If `buffer` is an object exposing the buffer interface, then
           all keywords are interpreted.

        No ``__init__`` method is needed because the array is fully initialized
        after the ``__new__`` method.

        Examples
        --------
        These examples illustrate the low-level `ndarray` constructor.  Refer
        to the `See Also` section above for easier ways of constructing an
        ndarray.

        First mode, `buffer` is None:

        >>> np.ndarray(shape=(2,2), dtype=float, order='F')
        array([[ -1.13698227e+002,   4.25087011e-303],
               [  2.88528414e-306,   3.27025015e-309]])         #random

        Second mode:

        >>> np.ndarray((2,), buffer=np.array([1,2,3]),
        ...            offset=np.int_().itemsize,
        ...            dtype=int) # offset = 1*itemsize, i.e. skip first element
        array([2, 3])
    """
    pass

    def __mul__(self, y): # real signature unknown; restored from __doc__
        """
            x.__mul__(y) <==> x*y

            Returns
            -------
            out : ndarray
        """
        pass

    def __neg__(self, *args, **kwargs): # real signature unknown
        """
            x.__neg__() <==> -x
            Returns
            -------
            out : ndarray
        """
        pass

    def __rmul__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rmul__(y) <==> y*x

            Returns
            -------
            out : ndarray
        """
        pass

    def __abs__(self): # real signature unknown; restored from __doc__
        """
            x.__abs__() <==> abs(x)

            Returns
            -------
            out : ndarray
        """
        pass

    def __add__(self, y): # real signature unknown; restored from __doc__
        """
            x.__add__(y) <==> x+y

            Returns
            -------
            out : ndarray
        """
        pass

    def __radd__(self, y): # real signature unknown; restored from __doc__
        """
            x.__radd__(y) <==> y+x

            Returns
            -------
            out : ndarray
        """
        pass

    def __copy__(self, order=None): # real signature unknown; restored from __doc__
        """
        a.__copy__([order])

            Return a copy of the array.

            Parameters
            ----------
            order : {'C', 'F', 'A'}, optional
                If order is 'C' (False) then the result is contiguous (default).
                If order is 'Fortran' (True) then the result has fortran order.
                If order is 'Any' (None) then the result has fortran order
                only if the array already is in fortran order.

            Returns
            -------
            out : ndarray
        """
        pass

    def __div__(self, y): # real signature unknown; restored from __doc__
        """
            x.__div__(y) <==> x/y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rdiv__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rdiv__(y) <==> y/x

            Returns
            -------
            out : ndarray
        """
        pass

    def __truediv__(self, y): # real signature unknown; restored from __doc__
        """
            x.__truediv__(y) <==> x/y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rtruediv__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rtruediv__(y) <==> y/x

            Returns
            -------
            out : ndarray
        """
        pass

    def __floordiv__(self, y): # real signature unknown; restored from __doc__
        """
            x.__floordiv__(y) <==> x//y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rfloordiv__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rfloordiv__(y) <==> y//x

            Returns
            -------
            out : ndarray
        """
        pass

    def __mod__(self, y): # real signature unknown; restored from __doc__
        """
            x.__mod__(y) <==> x%y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rmod__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rmod__(y) <==> y%x

            Returns
            -------
            out : ndarray
        """
        pass

    def __lshift__(self, y): # real signature unknown; restored from __doc__
        """
            x.__lshift__(y) <==> x<<y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rlshift__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rlshift__(y) <==> y<<x

            Returns
            -------
            out : ndarray
        """
        pass

    def __rshift__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rshift__(y) <==> x>>y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rrshift__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rrshift__(y) <==> y>>x

            Returns
            -------
            out : ndarray
        """
        pass

    def __and__(self, y): # real signature unknown; restored from __doc__
        """
            x.__and__(y) <==> x&y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rand__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rand__(y) <==> y&x

            Returns
            -------
            out : ndarray
        """
        pass

    def __or__(self, y): # real signature unknown; restored from __doc__
        """
            x.__or__(y) <==> x|y

            Returns
            -------
            out : ndarray
        """
        pass

    def __pos__(self, *args, **kwargs): # real signature unknown
        """
            x.__pos__() <==> +x

            Returns
            -------
            out : ndarray
        """
        pass

    def __ror__(self, y): # real signature unknown; restored from __doc__
        """
            x.__ror__(y) <==> y|x

            Returns
            -------
            out : ndarray
        """
        pass

    def __xor__(self, y): # real signature unknown; restored from __doc__
        """
            x.__xor__(y) <==> x^y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rxor__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rxor__(y) <==> y^x

            Returns
            -------
            out : ndarray
        """
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """
            x.__ge__(y) <==> x>=y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rge__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rge__(y) <==> y>=x

            Returns
            -------
            out : ndarray
        """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """
             x.__eq__(y) <==> x==y

            Returns
            -------
            out : ndarray
        """
        pass

    def __req__(self, y): # real signature unknown; restored from __doc__
        """
             x.__req__(y) <==> y==x

            Returns
            -------
            out : ndarray
        """
        pass

    def __sub__(self, y): # real signature unknown; restored from __doc__
        """
            x.__sub__(y) <==> x-y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rsub__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rsub__(y) <==> y-x

            Returns
            -------
            out : ndarray
        """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """
            x.__lt__(y) <==> x<y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rlt__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rlt__(y) <==> y<x

            Returns
            -------
            out : ndarray
        """
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """
            x.__le__(y) <==> x<=y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rle__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rle__(y) <==> y<=x

            Returns
            -------
            out : ndarray
        """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """
            x.__gt__(y) <==> x>y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rgt__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rgt__(y) <==> y>x

            Returns
            -------
            out : ndarray
        """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """
            x.__ne__(y) <==> x!=y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rne__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rne__(y) <==> y!=x

            Returns
            -------
            out : ndarray
        """
        pass

    def __iadd__(self, y): # real signature unknown; restored from __doc__
        """
            x.__iadd__(y) <==> x+=y

            Returns
            -------
            out : ndarray
        """
        pass

    def __riadd__(self, y): # real signature unknown; restored from __doc__
        """
            x.__riadd__(y) <==> y+=x

            Returns
            -------
            out : ndarray
        """
        pass

    def __isub__(self, y): # real signature unknown; restored from __doc__
        """
            x.__isub__(y) <==> x-=y

            Returns
            -------
            out : ndarray
        """
        pass

    def __risub__(self, y): # real signature unknown; restored from __doc__
        """
            x.__risub__(y) <==> y-=x

            Returns
            -------
            out : ndarray
        """
        pass

    def __imul__(self, y): # real signature unknown; restored from __doc__
        """
            x.__imul__(y) <==> x*=y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rimul__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rimul__(y) <==> y*=x

            Returns
            -------
            out : ndarray
        """
        pass

    def __idiv__(self, y): # real signature unknown; restored from __doc__
        """
            x.__idiv__(y) <==> x/=y

            Returns
            -------
            out : ndarray
        """
        pass

    def __ridiv__(self, y): # real signature unknown; restored from __doc__
        """
            x.__ridiv__(y) <==> y/=x

            Returns
            -------
            out : ndarray
        """
        pass

    def __itruediv__(self, y): # real signature unknown; restored from __doc__
        """
            x.__itruediv__(y) <==> x/y

            Returns
            -------
            out : ndarray
        """
        pass

    def __ritruediv__(self, y): # real signature unknown; restored from __doc__
        """
            x.__ritruediv__(y) <==> y/x

            Returns
            -------
            out : ndarray
        """
        pass

    def __ifloordiv__(self, y): # real signature unknown; restored from __doc__
        """
            x.__ifloordiv__(y) <==> x//y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rifloordiv__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rifloordiv__(y) <==> y//x

            Returns
            -------
            out : ndarray
        """
        pass

    def __imod__(self, y): # real signature unknown; restored from __doc__
        """
            x.__imod__(y) <==> x%=y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rimod__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rimod__(y) <==> y%=x

            Returns
            -------
            out : ndarray
        """
        pass

    def __ipow__(self, y): # real signature unknown; restored from __doc__
        """
            x.__ipow__(y) <==> x**=y

            Returns
            -------
            out : ndarray
        """
        pass

    def __ripow__(self, y): # real signature unknown; restored from __doc__
        """
            x.__ripow__(y) <==> y**=x

            Returns
            -------
            out : ndarray
        """
        pass

    def __ilshift__(self, y): # real signature unknown; restored from __doc__
        """
            x.__ilshift__(y) <==> x<<=y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rilshift__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rilshift__(y) <==> y<<=x

            Returns
            -------
            out : ndarray
        """
        pass

    def __irshift__(self, y): # real signature unknown; restored from __doc__
        """
            x.__irshift__(y) <==> x>>=y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rirshift__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rirshift__(y) <==> y>>=x

            Returns
            -------
            out : ndarray
        """
        pass

    def __iand__(self, y): # real signature unknown; restored from __doc__
        """
            x.__iand__(y) <==> x&=y

            Returns
            -------
            out : ndarray
        """
        pass

    def __riand__(self, y): # real signature unknown; restored from __doc__
        """
            x.__riand__(y) <==> y&=x

            Returns
            -------
            out : ndarray
        """
        pass

    def __invert__(self, *args, **kwargs): # real signature unknown
        """
            x.__invert__() <==> ~x

            Returns
            -------
            out : ndarray
        """
        pass

    def __ior__(self, y): # real signature unknown; restored from __doc__
        """
            x.__ior__(y) <==> x|=y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rior__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rior__(y) <==> y|=x

            Returns
            -------
            out : ndarray
        """
        pass

    def __ixor__(self, y): # real signature unknown; restored from __doc__
        """
            x.__ixor__(y) <==> x^=y

            Returns
            -------
            out : ndarray
        """
        pass

    def __rixor__(self, y): # real signature unknown; restored from __doc__
        """
            x.__rixor__(y) <==> y^=x

            Returns
            -------
            out : ndarray
        """
        pass

    def __pow__(self, y): # real signature unknown; restored from __doc__
        """
            x.__pow__(y) <==> x**y

            Returns
            -------
            out : ndarray
        """
        pass

    def __divmod__(self, y): # real signature unknown; restored from __doc__
        """
            x.__divmod__(y) <==> x%y

            Returns
            -------
            out : ndarray
        """
        pass