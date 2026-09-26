from typing import Any, ClassVar

from django import forms
from django.contrib.flatpages.models import FlatPage

class FlatpageForm(forms.ModelForm[FlatPage]):
    url: Any

    class Meta:
        model: ClassVar[type[FlatPage]]
        fields: ClassVar[str]

    def clean_url(self) -> str: ...
