from collections.abc import Iterable
from logging import Logger
from typing import Any, Generic

from django import forms
from django.contrib.auth.models import _User, _UserModel, _UserType
from django.contrib.auth.tokens import PasswordResetTokenGenerator
from django.core.exceptions import ValidationError
from django.db import models
from django.db.models.fields import _ErrorMessagesDict
from django.forms.fields import _ClassLevelWidgetT
from django.forms.widgets import Widget
from django.http.request import HttpRequest
from django.utils.functional import _StrOrPromise

logger: Logger
UserModel = _UserModel

class ReadOnlyPasswordHashWidget(forms.Widget):
    template_name: str
    read_only: bool
    def get_context(self, name: str, value: Any, attrs: dict[str, Any] | None) -> dict[str, Any]: ...

class ReadOnlyPasswordHashField(forms.Field):
    widget: _ClassLevelWidgetT
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...

class UsernameField(forms.CharField):
    def to_python(self, value: Any | None) -> Any | None: ...
    def widget_attrs(self, widget: Widget) -> dict[str, Any]: ...

class SetPasswordMixin(Generic[_UserType]):
    error_messages: _ErrorMessagesDict

    @staticmethod
    def create_password_fields(
        label1: _StrOrPromise = ..., label2: _StrOrPromise = ...
    ) -> tuple[forms.CharField, forms.CharField]: ...
    def validate_passwords(
        self,
        password1_field_name: str = ...,
        password2_field_name: str = ...,
    ) -> None: ...
    def validate_password_for_user(self, user: _UserType, password_field_name: str = "password2") -> None: ...
    def set_password_and_save(
        self, user: _UserType, password_field_name: str = "password1", commit: bool = True
    ) -> _UserType: ...

class SetUnusablePasswordMixin(Generic[_UserType]):
    usable_password_help_text: _StrOrPromise

    @staticmethod
    def create_usable_password_field(help_text: _StrOrPromise = ...) -> forms.ChoiceField: ...
    def validate_passwords(
        self,
        password1_field_name: str = ...,
        password2_field_name: str = ...,
        usable_password_field_name: str = ...,
    ) -> None: ...
    def validate_password_for_user(self, user: _UserType, **kwargs: Any) -> None: ...
    def set_password_and_save(self, user: _User, commit: bool = True, **kwargs: Any) -> _User: ...

class BaseUserCreationForm(forms.ModelForm[_UserType], Generic[_UserType]):
    error_messages: _ErrorMessagesDict
    password1: forms.Field
    password2: forms.Field
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    def save(self, commit: bool = ...) -> _UserType: ...

class UserCreationForm(BaseUserCreationForm[_UserType]):
    def clean_username(self) -> str: ...

class UserChangeForm(forms.ModelForm[_UserType]):
    password: forms.Field
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...

class AuthenticationForm(forms.Form):
    username: forms.Field
    password: forms.Field
    error_messages: _ErrorMessagesDict
    request: HttpRequest | None
    user_cache: _User | None
    username_field: models.Field
    def __init__(self, request: HttpRequest | None = ..., *args: Any, **kwargs: Any) -> None: ...
    def confirm_login_allowed(self, user: _User) -> None: ...
    def get_user(self) -> _User: ...
    def get_invalid_login_error(self) -> ValidationError: ...
    def clean(self) -> dict[str, Any]: ...

class PasswordResetForm(forms.Form):
    email: forms.Field
    def send_mail(
        self,
        subject_template_name: str,
        email_template_name: str,
        context: dict[str, Any],
        from_email: str | None,
        to_email: str,
        html_email_template_name: str | None = ...,
    ) -> None: ...
    def get_users(self, email: str) -> Iterable[_User]: ...
    def save(
        self,
        domain_override: str | None = ...,
        subject_template_name: str = ...,
        email_template_name: str = ...,
        use_https: bool = ...,
        token_generator: PasswordResetTokenGenerator = ...,
        from_email: str | None = ...,
        request: HttpRequest | None = ...,
        html_email_template_name: str | None = ...,
        extra_email_context: dict[str, str] | None = ...,
    ) -> None: ...

class SetPasswordForm(SetPasswordMixin, forms.Form, Generic[_UserType]):
    new_password1: forms.Field
    new_password2: forms.Field
    user: _UserType
    def __init__(self, user: _UserType, *args: Any, **kwargs: Any) -> None: ...
    def save(self, commit: bool = ...) -> _UserType: ...

class PasswordChangeForm(SetPasswordForm):
    old_password: forms.Field
    def clean_old_password(self) -> str: ...

class AdminPasswordChangeForm(forms.Form, Generic[_UserType]):
    error_messages: _ErrorMessagesDict
    required_css_class: str
    usable_password_help_text: str
    password1: forms.Field
    password2: forms.Field
    user: _UserType
    def __init__(self, user: _UserType, *args: Any, **kwargs: Any) -> None: ...
    def save(self, commit: bool = ...) -> _UserType: ...
    @property
    def changed_data(self) -> list[str]: ...

class AdminUserCreationForm(SetUnusablePasswordMixin, UserCreationForm):
    usable_password: forms.ChoiceField = ...
