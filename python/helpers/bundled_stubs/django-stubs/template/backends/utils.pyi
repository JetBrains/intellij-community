from typing import Any

from django.http.request import HttpRequest
from django.utils.safestring import SafeString

def csrf_input(request: HttpRequest) -> SafeString: ...

csrf_input_lazy: Any
csrf_token_lazy: Any
