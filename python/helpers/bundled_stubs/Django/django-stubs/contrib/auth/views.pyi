from typing import Any

from django.contrib.auth.base_user import _UserModel
from django.contrib.auth.forms import AuthenticationForm
from django.http.request import HttpRequest
from django.http.response import HttpResponse, HttpResponseRedirect
from django.views.generic.base import TemplateView
from django.views.generic.edit import FormView
from typing_extensions import TypeAlias

UserModel: TypeAlias = type[_UserModel]

class RedirectURLMixin:
    next_page: str | None
    redirect_field_name: str
    success_url_allowed_hosts: set[str]
    def get_success_url(self) -> str: ...
    def get_redirect_url(self) -> str: ...
    def get_success_url_allowed_hosts(self) -> set[str]: ...
    def get_default_redirect_url(self) -> str: ...

class LoginView(RedirectURLMixin, FormView[AuthenticationForm]):
    authentication_form: Any
    redirect_field_name: Any
    redirect_authenticated_user: bool
    extra_context: Any
    def get_redirect_url(self) -> str: ...

class LogoutView(RedirectURLMixin, TemplateView):
    next_page: str | None
    redirect_field_name: str
    extra_context: Any
    def dispatch(self, request: HttpRequest, *args: Any, **kwargs: Any) -> HttpResponse: ...
    def post(self, request: HttpRequest, *args: Any, **kwargs: Any) -> HttpResponse: ...
    def get_next_page(self) -> str | None: ...

def logout_then_login(request: HttpRequest, login_url: str | None = ...) -> HttpResponseRedirect: ...
def redirect_to_login(
    next: str, login_url: str | None = ..., redirect_field_name: str | None = ...
) -> HttpResponseRedirect: ...

class PasswordContextMixin:
    extra_context: Any
    def get_context_data(self, **kwargs: Any) -> dict[str, Any]: ...

class PasswordResetView(PasswordContextMixin, FormView):
    email_template_name: str
    extra_email_context: Any
    from_email: Any
    html_email_template_name: Any
    subject_template_name: str
    title: Any
    token_generator: Any

INTERNAL_RESET_URL_TOKEN: str
INTERNAL_RESET_SESSION_TOKEN: str

class PasswordResetDoneView(PasswordContextMixin, TemplateView):
    title: Any

class PasswordResetConfirmView(PasswordContextMixin, FormView):
    post_reset_login: bool
    post_reset_login_backend: Any
    reset_url_token: str
    title: Any
    token_generator: Any
    validlink: bool
    user: Any
    def get_user(self, uidb64: str) -> _UserModel | None: ...

class PasswordResetCompleteView(PasswordContextMixin, TemplateView):
    title: Any

class PasswordChangeView(PasswordContextMixin, FormView):
    title: Any

class PasswordChangeDoneView(PasswordContextMixin, TemplateView):
    title: Any
