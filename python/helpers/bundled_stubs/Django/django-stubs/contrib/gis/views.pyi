from django.contrib.gis.feeds import Feed
from django.http import HttpRequest, HttpResponse

def feed(request: HttpRequest, url: str, feed_dict: dict[str, type[Feed]] | None = ...) -> HttpResponse: ...
