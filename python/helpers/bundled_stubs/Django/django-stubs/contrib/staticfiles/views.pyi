from typing import Any

from django.http.request import HttpRequest
from django.http.response import FileResponse, HttpResponse

def serve(request: HttpRequest, path: str, insecure: bool = False, **kwargs: Any) -> HttpResponse | FileResponse: ...
