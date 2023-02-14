from typing import Any

from selenium.common.exceptions import WebDriverException as WebDriverException
from selenium.webdriver.common.desired_capabilities import DesiredCapabilities as DesiredCapabilities
from selenium.webdriver.remote.webdriver import WebDriver as RemoteWebDriver

from .remote_connection import SafariRemoteConnection as SafariRemoteConnection
from .service import Service as Service

class WebDriver(RemoteWebDriver):
    service: Any
    def __init__(
        self,
        port: int = ...,
        executable_path: str = ...,
        reuse_service: bool = ...,
        desired_capabilities=...,
        quiet: bool = ...,
        keep_alive: bool = ...,
        service_args: Any | None = ...,
    ) -> None: ...
    def quit(self) -> None: ...
    def set_permission(self, permission, value) -> None: ...
    def get_permission(self, permission): ...
    def debug(self) -> None: ...
