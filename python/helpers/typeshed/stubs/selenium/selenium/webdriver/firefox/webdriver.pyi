from collections.abc import Generator
from typing import Any

from selenium.webdriver.remote.webdriver import WebDriver as RemoteWebDriver

from .extension_connection import ExtensionConnection as ExtensionConnection
from .firefox_binary import FirefoxBinary as FirefoxBinary
from .firefox_profile import FirefoxProfile as FirefoxProfile
from .options import Options as Options
from .remote_connection import FirefoxRemoteConnection as FirefoxRemoteConnection
from .service import Service as Service
from .webelement import FirefoxWebElement as FirefoxWebElement

basestring = str

class WebDriver(RemoteWebDriver):
    NATIVE_EVENTS_ALLOWED: Any
    CONTEXT_CHROME: str
    CONTEXT_CONTENT: str
    binary: Any
    profile: Any
    service: Any
    def __init__(
        self,
        firefox_profile: Any | None = ...,
        firefox_binary: Any | None = ...,
        timeout: int = ...,
        capabilities: Any | None = ...,
        proxy: Any | None = ...,
        executable_path: str = ...,
        options: Any | None = ...,
        service_log_path: str = ...,
        firefox_options: Any | None = ...,
        service_args: Any | None = ...,
        desired_capabilities: Any | None = ...,
        log_path: Any | None = ...,
        keep_alive: bool = ...,
    ) -> None: ...
    def quit(self) -> None: ...
    @property
    def firefox_profile(self): ...
    def set_context(self, context) -> None: ...
    def context(self, context) -> Generator[None, None, None]: ...
    def install_addon(self, path, temporary: Any | None = ...): ...
    def uninstall_addon(self, identifier) -> None: ...
