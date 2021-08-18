from typing import Any

class UserAgentParser:
    platforms: Any
    browsers: Any
    def __init__(self): ...
    def __call__(self, user_agent): ...

class UserAgent:
    string: Any
    platform: str | None
    browser: str | None
    version: str | None
    language: str | None
    def __init__(self, environ_or_string): ...
    def to_header(self): ...
    def __nonzero__(self): ...
    __bool__: Any
