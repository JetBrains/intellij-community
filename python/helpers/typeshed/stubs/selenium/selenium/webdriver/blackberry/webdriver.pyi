from typing import Any

from selenium.webdriver.remote.webdriver import WebDriver as RemoteWebDriver

LOAD_TIMEOUT: int

class WebDriver(RemoteWebDriver):
    def __init__(
        self, device_password, bb_tools_dir: Any | None = ..., hostip: str = ..., port: int = ..., desired_capabilities=...
    ): ...
    def quit(self) -> None: ...
