def f(x):
    '''Does nothing.

    :type x: b.C
    '''
    pass


class C(object):
    def __eq__(self, other):
        '''
        :type self: :class:`C`
        :type other: :py:class:`b.C`
        '''
        return super(self, C).__eq__(other)
