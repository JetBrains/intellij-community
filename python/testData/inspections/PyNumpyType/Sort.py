def sort(self, axis=-1, kind='quicksort', order=None): # real signature unknown; restored from __doc__
    """
    a.sort(axis=-1, kind='quicksort', order=None)

        Sort an array, in-place.

        Parameters
        ----------
        axis : int, optional
            Axis along which to sort. Default is -1, which means sort along the
            last axis.
        kind : {'quicksort', 'mergesort', 'heapsort'}, optional
            Sorting algorithm. Default is 'quicksort'.
        order : list, optional
            When `a` is an array with fields defined, this argument specifies
            which fields to compare first, second, etc.  Not all fields need be
            specified.

    """
    pass

a = np.array([('a', 2), ('c', 1)], dtype=[('x', 'S1'), ('y', int)])
print(sort(a, order='y'))