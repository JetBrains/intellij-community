from typing import Any

class StreamLimitMiddleware:
    app = ...  # type: Any
    maximum_size = ...  # type: Any
    def __init__(self, app, maximum_size=...): ...
    def __call__(self, environ, start_response): ...
