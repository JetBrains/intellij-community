from typing import Any

from selenium.webdriver.remote.remote_connection import RemoteConnection as RemoteConnection

LOGGER: Any
PORT: int
HOST: Any

class ExtensionConnection(RemoteConnection):
    profile: Any
    binary: Any
    def __init__(self, host, firefox_profile, firefox_binary: Any | None = ..., timeout: int = ...) -> None: ...
    def quit(self, sessionId: Any | None = ...) -> None: ...
    def connect(self): ...
    @classmethod
    def connect_and_quit(cls) -> None: ...
    @classmethod
    def is_connectable(cls) -> None: ...

class ExtensionConnectionError(Exception): ...
