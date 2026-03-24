from typing import Any

from django import forms
from django.contrib import admin
from django.contrib.auth.models import Group, _UserType
from django.db.models.fields.related import ManyToManyField
from django.forms.models import ModelMultipleChoiceField
from django.http.request import HttpRequest
from django.http.response import HttpResponse
from typing_extensions import override

class GroupAdmin(admin.ModelAdmin[Group]):
    @override
    def formfield_for_manytomany(
        self, db_field: ManyToManyField, request: HttpRequest | None = ..., **kwargs: Any
    ) -> ModelMultipleChoiceField | None: ...

class UserAdmin(admin.ModelAdmin[_UserType]):
    change_user_password_template: Any
    add_fieldsets: Any
    add_form: Any
    change_password_form: Any
    @override
    def get_form(  # type: ignore[override]
        self, request: HttpRequest, obj: _UserType | None = ..., **kwargs: Any
    ) -> type[forms.ModelForm[_UserType]]: ...
    @override
    def lookup_allowed(self, lookup: str, value: str, request: HttpRequest) -> bool: ...
    def user_change_password(self, request: HttpRequest, id: str, form_url: str = ...) -> HttpResponse: ...
