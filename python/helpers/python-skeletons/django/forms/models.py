# TODO: Commit to separate repositoruy


from django.forms.formsets import BaseFormSet
class BaseModelFormSet(BaseFormSet):
    """
    A ``FormSet`` for editing a queryset and/or adding new objects to it.
    """
    model = None

    def __init__(self, data=None, files=None, auto_id='id_%s', prefix=None,
                 queryset=None, **kwargs):
        """
        :rtype: BaseModelFormSet[T <= BaseForm]
        """
        self.queryset = queryset
        self.initial_extra = kwargs.pop('initial', None)
        defaults = {'data': data, 'files': files, 'auto_id': auto_id, 'prefix': prefix}
        defaults.update(kwargs)
        super(BaseModelFormSet, self).__init__(**defaults)

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
