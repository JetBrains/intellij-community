from django.http.request import HttpRequest
from django.http.response import HttpResponseRedirect

def shortcut(request: HttpRequest, content_type_id: int | str, object_id: int | str) -> HttpResponseRedirect: ...
