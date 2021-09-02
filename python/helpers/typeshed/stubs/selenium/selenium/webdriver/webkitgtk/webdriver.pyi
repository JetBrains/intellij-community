from typing import Any

from selenium.webdriver.remote.webdriver import WebDriver as RemoteWebDriver

from .service import Service as Service

class WebDriver(RemoteWebDriver):
    service: Any
    def __init__(
        self,
        executable_path: str = ...,
        port: int = ...,
        options: Any | None = ...,
        desired_capabilities=...,
        service_log_path: Any | None = ...,
        keep_alive: bool = ...,
    ) -> None: ...
    def quit(self) -> None: ...
