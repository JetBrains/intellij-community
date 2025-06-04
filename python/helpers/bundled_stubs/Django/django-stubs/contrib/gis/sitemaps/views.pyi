from django.http import HttpRequest, HttpResponse

def kml(
    request: HttpRequest,
    label: str,
    model: str,
    field_name: str | None = ...,
    compress: bool = ...,
    using: str = ...,
) -> HttpResponse: ...
def kmz(
    request: HttpRequest, label: str, model: str, field_name: str | None = ..., using: str = ...
) -> HttpResponse: ...
