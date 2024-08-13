from collections.abc import Iterable
from typing import Any, TypeVar

from django import forms
from django.contrib.auth.base_user import AbstractBaseUser
from django.contrib.auth.tokens import PasswordResetTokenGenerator
from django.core.exceptions import ValidationError
from django.db import models
from django.db.models.fields import _ErrorMessagesDict
from django.forms.fields import _ClassLevelWidgetT
from django.forms.widgets import Widget
from django.http.request import HttpRequest

UserModel: type[AbstractBaseUser]
_User = TypeVar("_User", bound=AbstractBaseUser)

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

class BaseUserCreationForm(forms.ModelForm[_User]):
    error_messages: _ErrorMessagesDict
    password1: forms.Field
    password2: forms.Field
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...
    def clean_password2(self) -> str: ...
    def save(self, commit: bool = ...) -> _User: ...

class UserCreationForm(BaseUserCreationForm[_User]):
    def clean_username(self) -> str: ...

class UserChangeForm(forms.ModelForm[_User]):
    password: forms.Field
    def __init__(self, *args: Any, **kwargs: Any) -> None: ...

class AuthenticationForm(forms.Form):
    username: forms.Field
    password: forms.Field
    error_messages: _ErrorMessagesDict
    request: HttpRequest | None
    user_cache: Any
    username_field: models.Field
    def __init__(self, request: HttpRequest | None = ..., *args: Any, **kwargs: Any) -> None: ...
    def confirm_login_allowed(self, user: AbstractBaseUser) -> None: ...
    def get_user(self) -> AbstractBaseUser: ...
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
    def get_users(self, email: str) -> Iterable[AbstractBaseUser]: ...
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

class SetPasswordForm(forms.Form):
    error_messages: _ErrorMessagesDict
    new_password1: forms.Field
    new_password2: forms.Field
    user: AbstractBaseUser
    def __init__(self, user: AbstractBaseUser, *args: Any, **kwargs: Any) -> None: ...
    def clean_new_password2(self) -> str: ...
    def save(self, commit: bool = ...) -> AbstractBaseUser: ...

class PasswordChangeForm(SetPasswordForm):
    error_messages: _ErrorMessagesDict
    old_password: forms.Field
    def clean_old_password(self) -> str: ...

class AdminPasswordChangeForm(forms.Form):
    error_messages: _ErrorMessagesDict
    required_css_class: str
    password1: forms.Field
    password2: forms.Field
    user: AbstractBaseUser
    def __init__(self, user: AbstractBaseUser, *args: Any, **kwargs: Any) -> None: ...
    def clean_password2(self) -> str: ...
    def save(self, commit: bool = ...) -> AbstractBaseUser: ...
    @property
    def changed_data(self) -> list[str]: ...
