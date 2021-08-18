from typing import Any

from selenium.webdriver.chrome.webdriver import WebDriver as ChromiumDriver

from .options import Options as Options

class OperaDriver(ChromiumDriver):
    def __init__(
        self,
        executable_path: Any | None = ...,
        port: int = ...,
        options: Any | None = ...,
        service_args: Any | None = ...,
        desired_capabilities: Any | None = ...,
        service_log_path: Any | None = ...,
        opera_options: Any | None = ...,
        keep_alive: bool = ...,
    ) -> None: ...
    def create_options(self): ...

class WebDriver(OperaDriver):
    class ServiceType:
        CHROMIUM: int
    def __init__(
        self,
        desired_capabilities: Any | None = ...,
        executable_path: Any | None = ...,
        port: int = ...,
        service_log_path: Any | None = ...,
        service_args: Any | None = ...,
        options: Any | None = ...,
    ) -> None: ...
