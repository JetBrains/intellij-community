from typing import Any

XHTML_NAMESPACE: Any

def format_iso8601(obj): ...

class AtomFeed:
    default_generator: Any
    title: Any
    title_type: Any
    url: Any
    feed_url: Any
    id: Any
    updated: Any
    author: Any
    icon: Any
    logo: Any
    rights: Any
    rights_type: Any
    subtitle: Any
    subtitle_type: Any
    generator: Any
    links: Any
    entries: Any
    def __init__(self, title: Any | None = ..., entries: Any | None = ..., **kwargs): ...
    def add(self, *args, **kwargs): ...
    def generate(self): ...
    def to_string(self): ...
    def get_response(self): ...
    def __call__(self, environ, start_response): ...

class FeedEntry:
    title: Any
    title_type: Any
    content: Any
    content_type: Any
    url: Any
    id: Any
    updated: Any
    summary: Any
    summary_type: Any
    author: Any
    published: Any
    rights: Any
    links: Any
    categories: Any
    xml_base: Any
    def __init__(self, title: Any | None = ..., content: Any | None = ..., feed_url: Any | None = ..., **kwargs): ...
    def generate(self): ...
    def to_string(self): ...
