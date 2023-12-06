class property(object):
    """
    Property attribute.

      fget
        function to be used for getting an attribute value
      fset
        function to be used for setting an attribute value
      fdel
        function to be used for del'ing an attribute
      doc
        docstring

    Typical use is to define a managed attribute x:

    class C(object):
        def getx(self): return self._x
        def setx(self, value): self._x = value
        def delx(self): del self._x
        x = property(getx, setx, delx, "I'm the 'x' property.")

    Decorators make defining new properties or modifying existing ones easy:

    class C(object):
        @property
        def x(self):
            "I am the 'x' property."
            return self._x
        @x.setter
        def x(self, value):
            self._x = value
        @x.deleter
        def x(self):
            del self._x
    """
    def deleter(self, *args, **kwargs): # real signature unknown
        """ Descriptor to obtain a copy of the property with a different deleter. """
        pass

    def getter(self, *args, **kwargs): # real signature unknown
        """ Descriptor to obtain a copy of the property with a different getter. """
        pass

    def setter(self, *args, **kwargs): # real signature unknown
        """ Descriptor to obtain a copy of the property with a different setter. """
        pass

    def __delete__(self, *args, **kwargs): # real signature unknown
        """ Delete an attribute of instance. """
        pass

    def __getattribute__(self, *args, **kwargs): # real signature unknown
        """ Return getattr(self, name). """
        pass

    def __get__(self, *args, **kwargs): # real signature unknown
        """ Return an attribute of instance, which is of type owner. """
        pass

    def __init__(self, fget=None, fset=None, fdel=None, doc=None): # known special case of property.__init__
        """
        Property attribute.

          fget
            function to be used for getting an attribute value
          fset
            function to be used for setting an attribute value
          fdel
            function to be used for del'ing an attribute
          doc
            docstring

        Typical use is to define a managed attribute x:

        class C(object):
            def getx(self): return self._x
            def setx(self, value): self._x = value
            def delx(self): del self._x
            x = property(getx, setx, delx, "I'm the 'x' property.")

        Decorators make defining new properties or modifying existing ones easy:

        class C(object):
            @property
            def x(self):
                "I am the 'x' property."
                return self._x
            @x.setter
            def x(self, value):
                self._x = value
            @x.deleter
            def x(self):
                del self._x
        # (copied from class doc)
        """
        pass

    @staticmethod # known case of __new__
    def __new__(*args, **kwargs): # real signature unknown
        """ Create and return a new object.  See help(type) for accurate signature. """
        pass

    def __set__(self, *args, **kwargs): # real signature unknown
        """ Set an attribute of instance to value. """
        pass

    fdel = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    fget = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    fset = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    __isabstractmethod__ = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default


class IndexOpsMixin():
    """
    Common ops mixin to support a unified interface / docs for Series / Index
    """
    def tolist(self):
        """
        Return a list of the values.

        These are each a scalar type, which is a Python scalar
        (for str, int, float) or a pandas scalar
        (for Timestamp/Timedelta/Interval/Period)

        Returns
        -------
        list

        See Also
        --------
        numpy.ndarray.tolist : Return the array as an a.ndim-levels deep
            nested list of Python scalars.
        """
        # return self._values.tolist()
        ...

    to_list = tolist


class NDFrame:
    ...


class Series(IndexOpsMixin, NDFrame):
    """
    One-dimensional ndarray with axis labels (including time series).

    Labels need not be unique but must be a hashable type. The object
    supports both integer- and label-based indexing and provides a host of
    methods for performing operations involving the index. Statistical
    methods from ndarray have been overridden to automatically exclude
    missing data (currently represented as NaN).

    Operations between Series (+, -, /, \\*, \\*\\*) align values based on their
    associated index values-- they need not be the same length. The result
    index will be the sorted union of the two indexes.

    Parameters
    ----------
    data : array-like, Iterable, dict, or scalar value
        Contains data stored in Series. If data is a dict, argument order is
        maintained.
    index : array-like or Index (1d)
        Values must be hashable and have the same length as `data`.
        Non-unique index values are allowed. Will default to
        RangeIndex (0, 1, 2, ..., n) if not provided. If data is dict-like
        and index is None, then the keys in the data are used as the index. If the
        index is not None, the resulting Series is reindexed with the index values.
    dtype : str, numpy.dtype, or ExtensionDtype, optional
        Data type for the output Series. If not specified, this will be
        inferred from `data`.
        See the :ref:`user guide <basics.dtypes>` for more usages.
    name : str, optional
        The name to give to the Series.
    copy : bool, default False
        Copy input data. Only affects Series or 1d ndarray input. See examples.

    Examples
    --------
    Constructing Series from a dictionary with an Index specified

    # >>> d = {'a': 1, 'b': 2, 'c': 3}
    # >>> ser = pd.Series(data=d, index=['a', 'b', 'c'])
    # >>> ser
    a   1
    b   2
    c   3
    dtype: int64

    The keys of the dictionary match with the Index values, hence the Index
    values have no effect.

    # >>> d = {'a': 1, 'b': 2, 'c': 3}
    # >>> ser = pd.Series(data=d, index=['x', 'y', 'z'])
    # >>> ser
    x   NaN
    y   NaN
    z   NaN
    dtype: float64

    Note that the Index is first build with the keys from the dictionary.
    After this the Series is reindexed with the given Index values, hence we
    get all NaN as a result.

    Constructing Series from a list with `copy=False`.

    # >>> r = [1, 2]
    # >>> ser = pd.Series(r, copy=False)
    # >>> ser.iloc[0] = 999
    # >>> r
    [1, 2]
    # >>> ser
    0    999
    1      2
    dtype: int64

    Due to input data type the Series has a `copy` of
    the original data even though `copy=False`, so
    the data is unchanged.

    Constructing Series from a 1d ndarray with `copy=False`.

    # >>> r = np.array([1, 2])
    # >>> ser = pd.Series(r, copy=False)
    # >>> ser.iloc[0] = 999
    # >>> r
    array([999,   2])
    # >>> ser
    0    999
    1      2
    dtype: int64

    Due to input data type the Series has a `view` on
    the original data, so
    the data is changed as well.
    """
    def __init__(
            self,
            data=None,
            index=None,
            dtype = None,
            name=None,
            copy = False,
            fastpath = False,
    ):
        ...

    @property
    def values(self):
        """
        Return Series as ndarray or ndarray-like depending on the dtype.

        .. warning::

           We recommend using :attr:`Series.array` or
           :meth:`Series.to_numpy`, depending on whether you need
           a reference to the underlying data or a NumPy array.

        Returns
        -------
        numpy.ndarray or ndarray-like

        See Also
        --------
        Series.array : Reference to the underlying data.
        Series.to_numpy : A NumPy array representing the underlying data.

        Examples
        --------
        # >>> pd.Series([1, 2, 3]).values
        array([1, 2, 3])

        # >>> pd.Series(list('aabc')).values
        array(['a', 'a', 'b', 'c'], dtype=object)

        # >>> pd.Series(list('aabc')).astype('category').values
        ['a', 'a', 'b', 'c']
        Categories (3, object): ['a', 'b', 'c']

        Timezone aware datetime data is converted to UTC:

        # >>> pd.Series(pd.date_range('20130101', periods=3,
        ...                         tz='US/Eastern')).values
        array(['2013-01-01T05:00:00.000000000',
               '2013-01-02T05:00:00.000000000',
               '2013-01-03T05:00:00.000000000'], dtype='datetime64[ns]')
        """
        # return self._mgr.external_values()
        ...