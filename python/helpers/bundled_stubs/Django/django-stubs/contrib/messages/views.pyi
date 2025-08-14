from typing import Generic, TypeVar

from django.forms.forms import BaseForm
from django.http.response import HttpResponse
from django.utils.functional import _StrOrPromise

_F = TypeVar("_F", bound=BaseForm)

class SuccessMessageMixin(Generic[_F]):
    success_message: _StrOrPromise
    def form_valid(self, form: _F) -> HttpResponse: ...
    def get_success_message(self, cleaned_data: dict[str, str]) -> _StrOrPromise: ...
