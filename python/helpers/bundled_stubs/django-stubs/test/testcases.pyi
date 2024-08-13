import threading
import unittest
from collections.abc import Callable, Collection, Generator, Iterable, Iterator, Mapping, Sequence
from contextlib import contextmanager
from types import TracebackType
from typing import Any, overload

from django.core.exceptions import ImproperlyConfigured
from django.core.handlers.wsgi import WSGIHandler
from django.core.servers.basehttp import ThreadedWSGIServer, WSGIRequestHandler
from django.db import connections as connections
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.base import Model
from django.db.models.query import QuerySet, RawQuerySet
from django.forms import BaseFormSet, Form
from django.forms.fields import EmailField
from django.http import HttpRequest
from django.http.response import FileResponse, HttpResponseBase
from django.template.base import Template
from django.test.client import AsyncClient, Client
from django.test.html import Element
from django.test.utils import CaptureQueriesContext, ContextList
from django.utils.functional import classproperty
from typing_extensions import Self

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
    def __init__(self, test_case: Any, template_name: Any) -> None: ...
    def on_template_render(self, sender: Any, signal: Any, template: Any, context: Any, **kwargs: Any) -> None: ...
    def test(self) -> None: ...
    def message(self) -> str: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        exc_tb: TracebackType | None,
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
    allow_database_queries: bool
    # TODO: str -> Literal['__all__']
    databases: set[str] | str
    def __call__(self, result: unittest.TestResult | None = ...) -> None: ...
    def settings(self, **kwargs: Any) -> Any: ...
    def modify_settings(self, **kwargs: Any) -> Any: ...
    def assertRedirects(
        self,
        response: HttpResponseBase,
        expected_url: str,
        status_code: int = ...,
        target_status_code: int = ...,
        msg_prefix: str = ...,
        fetch_redirect_response: bool = ...,
    ) -> None: ...
    def assertURLEqual(
        self,
        url1: str | Any,  # Any for reverse_lazy() support
        url2: str | Any,
        msg_prefix: str = ...,
    ) -> None: ...
    def assertContains(
        self,
        response: HttpResponseBase,
        text: bytes | int | str,
        count: int | None = ...,
        status_code: int = ...,
        msg_prefix: str = ...,
        html: bool = ...,
    ) -> None: ...
    def assertNotContains(
        self,
        response: HttpResponseBase,
        text: bytes | str,
        status_code: int = ...,
        msg_prefix: str = ...,
        html: bool = ...,
    ) -> None: ...
    def assertFormError(
        self,
        form: Form,
        field: str | None,
        errors: list[str] | str,
        msg_prefix: str = ...,
    ) -> None: ...
    # assertFormsetError (lowercase "set") deprecated in Django 4.2
    def assertFormsetError(
        self,
        formset: BaseFormSet,
        form_index: int | None,
        field: str | None,
        errors: list[str] | str,
        msg_prefix: str = ...,
    ) -> None: ...
    def assertFormSetError(
        self,
        formset: BaseFormSet,
        form_index: int | None,
        field: str | None,
        errors: list[str] | str,
        msg_prefix: str = ...,
    ) -> None: ...
    def assertTemplateUsed(
        self,
        response: HttpResponseBase | str | None = ...,
        template_name: str | None = ...,
        msg_prefix: str = ...,
        count: int | None = ...,
    ) -> _AssertTemplateUsedContext | None: ...
    def assertTemplateNotUsed(
        self, response: HttpResponseBase | str = ..., template_name: str | None = ..., msg_prefix: str = ...
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
        field_args: Iterable[Any] | None = ...,
        field_kwargs: Mapping[str, Any] | None = ...,
        empty_value: str = ...,
    ) -> Any: ...
    def assertHTMLEqual(self, html1: str, html2: str, msg: str | None = ...) -> None: ...
    def assertHTMLNotEqual(self, html1: str, html2: str, msg: str | None = ...) -> None: ...
    def assertInHTML(self, needle: str, haystack: str, count: int | None = ..., msg_prefix: str = ...) -> None: ...
    def assertJSONEqual(
        self,
        raw: str,
        expected_data: dict[str, Any] | list[Any] | str | int | float | bool | None,
        msg: str | None = ...,
    ) -> None: ...
    def assertJSONNotEqual(
        self,
        raw: str,
        expected_data: dict[str, Any] | list[Any] | str | int | float | bool | None,
        msg: str | None = ...,
    ) -> None: ...
    def assertXMLEqual(self, xml1: str, xml2: str, msg: str | None = ...) -> None: ...
    def assertXMLNotEqual(self, xml1: str, xml2: str, msg: str | None = ...) -> None: ...

class TransactionTestCase(SimpleTestCase):
    reset_sequences: bool
    available_apps: Any
    fixtures: Any
    multi_db: bool
    serialized_rollback: bool
    # assertQuerysetEqual (lowercase "set") deprecated in Django 4.2
    def assertQuerysetEqual(
        self,
        qs: Iterator[Any] | list[Model] | QuerySet | RawQuerySet,
        values: Collection[Any],
        transform: Callable[[Model], Any] | type[str] = ...,
        ordered: bool = ...,
        msg: str | None = ...,
    ) -> None: ...
    def assertQuerySetEqual(
        self,
        qs: Iterator[Any] | list[Model] | QuerySet | RawQuerySet,
        values: Collection[Any],
        transform: Callable[[Model], Any] | type[str] | None = ...,
        ordered: bool = ...,
        msg: str | None = ...,
    ) -> None: ...
    @overload
    def assertNumQueries(self, num: int, func: None = ..., *, using: str = ...) -> _AssertNumQueriesContext: ...
    @overload
    def assertNumQueries(
        self, num: int, func: Callable[..., Any], *args: Any, using: str = ..., **kwargs: Any
    ) -> None: ...

class TestCase(TransactionTestCase):
    @classmethod
    def setUpTestData(cls) -> None: ...
    @classmethod
    @contextmanager
    def captureOnCommitCallbacks(
        cls, *, using: str = ..., execute: bool = ...
    ) -> Generator[list[Callable[[], Any]], None, None]: ...

class CheckCondition:
    conditions: Sequence[tuple[Callable, str]]
    def __init__(self, *conditions: tuple[Callable, str]) -> None: ...
    def add_condition(self, condition: Callable, reason: str) -> CheckCondition: ...
    def __get__(self, instance: None, cls: type[TransactionTestCase] | None = ...) -> bool: ...

def skipIfDBFeature(*features: Any) -> Callable: ...
def skipUnlessDBFeature(*features: Any) -> Callable: ...
def skipUnlessAnyDBFeature(*features: Any) -> Callable: ...

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
    connections_override: dict[str, BaseDatabaseWrapper]
    def __init__(
        self,
        host: str,
        static_handler: type[WSGIHandler],
        connections_override: dict[str, BaseDatabaseWrapper] = ...,
        port: int = ...,
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
    @classmethod
    def tearDownClass(cls) -> None: ...

def connections_support_transactions(aliases: Iterable[str] | None = ...) -> bool: ...
