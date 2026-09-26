from collections.abc import Callable

from django.http.request import HttpRequest
from django.utils.functional import _StrPromise
from django.utils.safestring import SafeString

def csrf_input(request: HttpRequest) -> SafeString: ...

csrf_input_lazy: Callable[[HttpRequest], SafeString]
csrf_token_lazy: Callable[[HttpRequest], _StrPromise]
