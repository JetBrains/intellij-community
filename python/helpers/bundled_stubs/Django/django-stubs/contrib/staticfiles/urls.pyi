from django.urls import URLPattern, _AnyURL

urlpatterns: list[_AnyURL]

def staticfiles_urlpatterns(prefix: str | None = ...) -> list[URLPattern]: ...
