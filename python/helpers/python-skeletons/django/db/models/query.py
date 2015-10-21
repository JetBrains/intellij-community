"""
QuerySet is parametrized with model object
"""
class QuerySet(object):
    def __init__(self, model=None, query=None, using=None, hints=None):
        """
        :rtype: django.db.models.query.QuerySet[T]
        """
        pass

    def __iter__(self):
        """

        :rtype: collections.Iterator[T]
        """
        pass
