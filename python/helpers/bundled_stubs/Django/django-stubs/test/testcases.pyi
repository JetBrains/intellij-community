import threading
import unittest
from collections.abc import Callable, Iterable, Iterator, Mapping, Sequence
from contextlib import AbstractContextManager
from types import TracebackType
from typing import Any, overload

from django.core.exceptions import ImproperlyConfigured
from django.core.handlers.wsgi import WSGIHandler
from django.core.servers.basehttp import ThreadedWSGIServer, WSGIRequestHandler
from django.db import connections as connections
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.base import Model
from django.db.models.query import QuerySet, RawQuerySet
from django.forms import BaseForm, BaseFormSet
from django.forms.fields import EmailField
from django.http import HttpRequest
from django.http.response import FileResponse, HttpResponseBase
from django.template.base import Template
from django.test.client import AsyncClient, Client
from django.test.html import Element
from django.test.utils import CaptureQueriesContext, ContextList
from django.utils.functional import _StrOrPromise, classproperty
from typing_extensions import Self, override

def to_list(value: Any) -> list[Any]: ...
def assert_and_parse_html(self: Any, html: str, user_msg: str, msg: str) -> Element: ...

class _AssertNumQueriesContext(CaptureQueriesContext):
    test_case: SimpleTestCase
    num: int
    def __init__(self, test_case: Any, num: Any, connection: BaseDatabaseWrapper) -> None: ...

class _AssertTemplateUsedContext:
    test_case: SimpleTestCase
    template_name: str
    rendered_templates: list[Template]
    context: ContextList
    def __init__(self, test_case: Any, template_name: Any, msg_prefix: str = "", count: int | None = None) -> None: ...
    def on_template_render(self, sender: Any, signal: Any, template: Any, context: Any, **kwargs: Any) -> None: ...
    @property
    def rendered_template_names(self) -> list[str]: ...
    def test(self) -> None: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        traceback: TracebackType | None,
    ) -> None: ...

class _AssertTemplateNotUsedContext(_AssertTemplateUsedContext): ...

class _DatabaseFailure:
    wrapped: Any
    message: str
    def __init__(self, wrapped: Any, message: str) -> None: ...
    def __call__(self) -> None: ...

class SimpleTestCase(unittest.TestCase):
    client_class: type[Client]
    client: Client
    async_client_class: type[AsyncClient]
    async_client: AsyncClient
    # TODO: str -> Literal['__all__']
    databases: set[str] | str
    @classmethod
    def ensure_connection_patch_method(cls) -> None: ...
    @override
    def __call__(self, result: unittest.TestResult | None = None) -> None: ...
    def settings(self, **kwargs: Any) -> Any: ...
    def modify_settings(self, **kwargs: Any) -> Any: ...
    def assertRedirects(
        self,
        response: HttpResponseBase,
        expected_url: str,
        status_code: int = 302,
        target_status_code: int = 200,
        msg_prefix: str = "",
        fetch_redirect_response: bool = True,
    ) -> None: ...
    def assertURLEqual(
        self,
        url1: _StrOrPromise,
        url2: _StrOrPromise,
        msg_prefix: str = "",
    ) -> None: ...
    def assertContains(
        self,
        response: HttpResponseBase,
        text: bytes | int | _StrOrPromise,
        count: int | None = None,
        status_code: int = 200,
        msg_prefix: str = "",
        html: bool = False,
    ) -> None: ...
    def assertNotContains(
        self,
        response: HttpResponseBase,
        text: bytes | int | _StrOrPromise,
        status_code: int = 200,
        msg_prefix: str = "",
        html: bool = False,
    ) -> None: ...
    def assertFormError(
        self,
        form: BaseForm,
        field: str | None,
        errors: list[str] | str,
        msg_prefix: str = "",
    ) -> None: ...
    def assertFormSetError(
        self,
        formset: BaseFormSet,
        form_index: int | None,
        field: str | None,
        errors: list[str] | str,
        msg_prefix: str = "",
    ) -> None: ...
    def assertTemplateUsed(
        self,
        response: HttpResponseBase | str | None = None,
        template_name: str | None = None,
        msg_prefix: str = "",
        count: int | None = None,
    ) -> _AssertTemplateUsedContext | None: ...
    def assertTemplateNotUsed(
        self, response: HttpResponseBase | str | None = None, template_name: str | None = None, msg_prefix: str = ""
    ) -> _AssertTemplateNotUsedContext | None: ...
    def assertRaisesMessage(
        self, expected_exception: type[Exception], expected_message: str, *args: Any, **kwargs: Any
    ) -> Any: ...
    def assertWarnsMessage(
        self, expected_warning: type[Exception], expected_message: str, *args: Any, **kwargs: Any
    ) -> Any: ...
    def assertFieldOutput(
        self,
        fieldclass: type[EmailField],
        valid: dict[str, str],
        invalid: dict[str, list[str]],
        field_args: Iterable[Any] | None = None,
        field_kwargs: Mapping[str, Any] | None = None,
        empty_value: str = "",
    ) -> Any: ...
    def assertHTMLEqual(self, html1: str, html2: str, msg: str | None = None) -> None: ...
    def assertHTMLNotEqual(self, html1: str, html2: str, msg: str | None = None) -> None: ...
    def assertInHTML(self, needle: str, haystack: str, count: int | None = None, msg_prefix: str = "") -> None: ...
    def assertNotInHTML(self, needle: str, haystack: str, msg_prefix: str = "") -> None: ...
    def assertJSONEqual(
        self,
        raw: str | bytes | bytearray,
        expected_data: dict[str, Any] | list[Any] | str | int | float | bool | None,
        msg: str | None = None,
    ) -> None: ...
    def assertJSONNotEqual(
        self,
        raw: str | bytes | bytearray,
        expected_data: dict[str, Any] | list[Any] | str | int | float | bool | None,
        msg: str | None = None,
    ) -> None: ...
    def assertXMLEqual(self, xml1: str, xml2: str, msg: str | None = None) -> None: ...
    def assertXMLNotEqual(self, xml1: str, xml2: str, msg: str | None = None) -> None: ...

class TransactionTestCase(SimpleTestCase):
    reset_sequences: bool
    available_apps: Any
    fixtures: Any
    serialized_rollback: bool
    def assertQuerySetEqual(
        self,
        qs: Iterator[Any] | list[Model] | QuerySet | RawQuerySet,
        values: Iterable[Any],
        transform: Callable[[Model], Any] | type[str] | None = None,
        ordered: bool = True,
        msg: str | None = None,
    ) -> None: ...
    @overload
    def assertNumQueries(self, num: int, func: None = None, *, using: str = ...) -> _AssertNumQueriesContext: ...
    @overload
    def assertNumQueries(
        self, num: int, func: Callable[..., Any], *args: Any, using: str = ..., **kwargs: Any
    ) -> None: ...

class TestCase(TransactionTestCase):
    @classmethod
    def setUpTestData(cls) -> None: ...
    @classmethod
    def captureOnCommitCallbacks(
        cls, *, using: str = "default", execute: bool = False
    ) -> AbstractContextManager[list[Callable[[], Any]]]: ...

class CheckCondition:
    conditions: Sequence[tuple[Callable[..., Any], str]]
    def __init__(self, *conditions: tuple[Callable[..., Any], str]) -> None: ...
    def add_condition(self, condition: Callable[..., Any], reason: str) -> CheckCondition: ...
    def __get__(self, instance: None, cls: type[TransactionTestCase] | None = ...) -> bool: ...

def connections_support_transactions(aliases: Iterable[str] | None = None) -> bool: ...
def skipIfDBFeature(*features: Any) -> Callable[..., Any]: ...
def skipUnlessDBFeature(*features: Any) -> Callable[..., Any]: ...
def skipUnlessAnyDBFeature(*features: Any) -> Callable[..., Any]: ...

class QuietWSGIRequestHandler(WSGIRequestHandler): ...

class FSFilesHandler(WSGIHandler):
    application: Any
    base_url: Any
    def __init__(self, application: Any) -> None: ...
    def file_path(self, url: Any) -> str: ...
    def serve(self, request: HttpRequest) -> FileResponse: ...

class _StaticFilesHandler(FSFilesHandler):
    def get_base_dir(self) -> str: ...
    def get_base_url(self) -> str: ...

class _MediaFilesHandler(FSFilesHandler):
    def get_base_dir(self) -> str: ...
    def get_base_url(self) -> str: ...

class LiveServerThread(threading.Thread):
    host: str
    port: int
    is_ready: threading.Event
    error: ImproperlyConfigured | None
    static_handler: type[WSGIHandler]
    connections_override: dict[str, BaseDatabaseWrapper] | None
    server_class: type[ThreadedWSGIServer]
    def __init__(
        self,
        host: str,
        static_handler: type[WSGIHandler],
        connections_override: dict[str, BaseDatabaseWrapper] | None = None,
        port: int = 0,
    ) -> None: ...
    httpd: ThreadedWSGIServer
    def terminate(self) -> None: ...

class LiveServerTestCase(TransactionTestCase):
    host: str
    port: int
    server_thread_class: type[Any]
    server_thread: Any
    static_handler: Any
    @classproperty
    def live_server_url(cls: Any) -> str: ...
    @classproperty
    def allowed_host(cls: Any) -> str: ...

class SerializeMixin:
    lockfile: Any
    @classmethod
    def setUpClass(cls) -> None: ...

__all__ = ("SimpleTestCase", "TestCase", "TransactionTestCase", "skipIfDBFeature", "skipUnlessDBFeature")
