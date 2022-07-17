from django.forms.boundfield import BoundField
class BaseForm(object):
    """
    This is the main implementation of all the Form logic. Note that this
    class is different than Form. See the comments by the Form class for more
    information. Any improvements to the form API should be made to *this*
    class, not to the Form class.

    """

    def __iter__(self):
        """

        :rtype: collections.Iterator[BoundField]
        """
        pass

    def __getitem__(self, index):
        """
        :rtype: BoundField
        """
        pass