from typing import Any

from selenium.webdriver.remote.webdriver import WebDriver as RemoteWebDriver

from .options import Options as Options
from .service import Service as Service

DEFAULT_TIMEOUT: int
DEFAULT_PORT: int
DEFAULT_HOST: Any
DEFAULT_LOG_LEVEL: Any
DEFAULT_SERVICE_LOG_PATH: Any

class WebDriver(RemoteWebDriver):
    port: Any
    host: Any
    iedriver: Any
    def __init__(
        self,
        executable_path: str = ...,
        capabilities: Any | None = ...,
        port=...,
        timeout=...,
        host=...,
        log_level=...,
        service_log_path=...,
        options: Any | None = ...,
        ie_options: Any | None = ...,
        desired_capabilities: Any | None = ...,
        log_file: Any | None = ...,
        keep_alive: bool = ...,
    ) -> None: ...
    def quit(self) -> None: ...
    def create_options(self): ...
