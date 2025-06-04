from typing import Any

from django.http.request import HttpRequest
from django.http.response import FileResponse

def serve(request: HttpRequest, path: str, insecure: bool = ..., **kwargs: Any) -> FileResponse: ...
