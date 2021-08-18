from typing import Any

from selenium.webdriver.remote.webdriver import WebDriver as RemoteWebDriver

from .remote_connection import ChromeRemoteConnection as ChromeRemoteConnection

class WebDriver(RemoteWebDriver):
    service: Any
    def __init__(
        self,
        executable_path: str = ...,
        port: int = ...,
        options: Any | None = ...,
        service_args: Any | None = ...,
        desired_capabilities: Any | None = ...,
        service_log_path: Any | None = ...,
        chrome_options: Any | None = ...,
        keep_alive: bool = ...,
    ) -> None: ...
    def launch_app(self, id): ...
    def get_network_conditions(self): ...
    def set_network_conditions(self, **network_conditions) -> None: ...
    def execute_cdp_cmd(self, cmd, cmd_args): ...
    def quit(self) -> None: ...
    def create_options(self): ...
