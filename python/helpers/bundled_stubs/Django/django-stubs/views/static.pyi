from pathlib import Path
from typing import Any

from django.http import FileResponse, HttpResponse
from django.http.request import HttpRequest

def builtin_template_path(name: str) -> Path: ...
def serve(
    request: HttpRequest, path: str, document_root: str | None = None, show_indexes: bool = False
) -> HttpResponse | FileResponse: ...

template_translatable: Any

def directory_index(path: Any, fullpath: Any) -> HttpResponse: ...
def was_modified_since(header: str | None = None, mtime: float = 0) -> bool: ...
