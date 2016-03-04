from django.forms import Form
class BaseFormSet(object):
    """
    A collection of instances of the same Form class.

    """

    def __init__(self, data=None, files=None, auto_id='id_%s', prefix=None,
                 initial=None, error_class=ErrorList, form_kwargs=None):
        """
        :rtype: BaseFormSet[T <= Form]
        """

    def __iter__(self):
        """

        :rtype: collections.Iterator[T]
        """
        pass

    def __getitem__(self, index):
        """
        :rtype: T
        """
        pass
