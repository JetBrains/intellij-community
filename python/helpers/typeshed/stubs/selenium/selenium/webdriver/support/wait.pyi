from typing import Any

from selenium.common.exceptions import NoSuchElementException as NoSuchElementException, TimeoutException as TimeoutException

POLL_FREQUENCY: float
IGNORED_EXCEPTIONS: Any

class WebDriverWait:
    def __init__(self, driver, timeout, poll_frequency=..., ignored_exceptions: Any | None = ...) -> None: ...
    def until(self, method, message: str = ...): ...
    def until_not(self, method, message: str = ...): ...
