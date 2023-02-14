from typing import Any

from selenium.webdriver.common import service as service

class Service(service.Service):
    service_args: Any
    quiet: Any
    def __init__(self, executable_path, port: int = ..., quiet: bool = ..., service_args: Any | None = ...) -> None: ...
    def command_line_args(self): ...
    @property
    def service_url(self): ...
