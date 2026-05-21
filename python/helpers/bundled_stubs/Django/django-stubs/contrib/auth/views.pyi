from collections.abc import Mapping
from typing import Any, TypeVar

from django.contrib.auth.forms import AuthenticationForm, PasswordChangeForm, PasswordResetForm, SetPasswordForm
from django.contrib.auth.models import _User, _UserModel
from django.http.request import HttpRequest
from django.http.response import HttpResponse, HttpResponseRedirect
from django.utils.functional import _StrOrPromise
from django.views.generic.base import TemplateView
from django.views.generic.edit import FormView
from typing_extensions import override

UserModel = _UserModel
_AuthForm = TypeVar("_AuthForm", bound=AuthenticationForm, default=AuthenticationForm)

class RedirectURLMixin:
    next_page: str | None
    redirect_field_name: str
    success_url_allowed_hosts: set[str]
    def get_success_url(self) -> str: ...
    def get_redirect_url(self) -> str: ...
    def get_success_url_allowed_hosts(self) -> set[str]: ...
    def get_default_redirect_url(self) -> str: ...

class LoginView(RedirectURLMixin, FormView[_AuthForm]):
    form_class: type[AuthenticationForm]  # type: ignore[assignment]
    authentication_form: type[_AuthForm] | None
    redirect_field_name: str
    redirect_authenticated_user: bool
    extra_context: Mapping[str, Any] | None

class LogoutView(RedirectURLMixin, TemplateView):
    next_page: str | None
    redirect_field_name: str
    extra_context: Mapping[str, Any] | None
    @override
    def dispatch(self, request: HttpRequest, *args: Any, **kwargs: Any) -> HttpResponse: ...
    def post(self, request: HttpRequest, *args: Any, **kwargs: Any) -> HttpResponse: ...

def logout_then_login(request: HttpRequest, login_url: str | None = ...) -> HttpResponseRedirect: ...
def redirect_to_login(
    next: str, login_url: str | None = ..., redirect_field_name: str | None = ...
) -> HttpResponseRedirect: ...

class PasswordContextMixin:
    extra_context: Mapping[str, Any] | None
    def get_context_data(self, **kwargs: Any) -> dict[str, Any]: ...

class PasswordResetView(PasswordContextMixin, FormView[PasswordResetForm]):
    email_template_name: str
    extra_email_context: Mapping[str, Any] | None
    form_class: type[PasswordResetForm]
    from_email: str | None
    html_email_template_name: str | None
    subject_template_name: str
    title: _StrOrPromise
    token_generator: Any

INTERNAL_RESET_SESSION_TOKEN: str

class PasswordResetDoneView(PasswordContextMixin, TemplateView):
    title: _StrOrPromise

class PasswordResetConfirmView(PasswordContextMixin, FormView[SetPasswordForm]):
    form_class: type[SetPasswordForm]
    post_reset_login: bool
    post_reset_login_backend: str | None
    reset_url_token: str
    title: _StrOrPromise
    token_generator: Any
    validlink: bool
    user: _User | None
    def get_user(self, uidb64: str) -> _User | None: ...

class PasswordResetCompleteView(PasswordContextMixin, TemplateView):
    title: _StrOrPromise

class PasswordChangeView(PasswordContextMixin, FormView[PasswordChangeForm]):
    form_class: type[PasswordChangeForm]
    title: _StrOrPromise

class PasswordChangeDoneView(PasswordContextMixin, TemplateView):
    title: _StrOrPromise
