from typing import Any

from selenium.webdriver.remote.webdriver import WebDriver as RemoteWebDriver

class WebDriver(RemoteWebDriver):
    port: Any
    edge_service: Any
    def __init__(
        self,
        executable_path: str = ...,
        capabilities: Any | None = ...,
        port: int = ...,
        verbose: bool = ...,
        service_log_path: Any | None = ...,
        log_path: Any | None = ...,
        keep_alive: bool = ...,
    ) -> None: ...
    def quit(self) -> None: ...
