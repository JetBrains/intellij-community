from typing import Any

XHTML_NAMESPACE = ...  # type: Any

def format_iso8601(obj): ...

class AtomFeed:
    default_generator = ...  # type: Any
    title = ...  # type: Any
    title_type = ...  # type: Any
    url = ...  # type: Any
    feed_url = ...  # type: Any
    id = ...  # type: Any
    updated = ...  # type: Any
    author = ...  # type: Any
    icon = ...  # type: Any
    logo = ...  # type: Any
    rights = ...  # type: Any
    rights_type = ...  # type: Any
    subtitle = ...  # type: Any
    subtitle_type = ...  # type: Any
    generator = ...  # type: Any
    links = ...  # type: Any
    entries = ...  # type: Any
    def __init__(self, title=None, entries=None, **kwargs): ...
    def add(self, *args, **kwargs): ...
    def generate(self): ...
    def to_string(self): ...
    def get_response(self): ...
    def __call__(self, environ, start_response): ...

class FeedEntry:
    title = ...  # type: Any
    title_type = ...  # type: Any
    content = ...  # type: Any
    content_type = ...  # type: Any
    url = ...  # type: Any
    id = ...  # type: Any
    updated = ...  # type: Any
    summary = ...  # type: Any
    summary_type = ...  # type: Any
    author = ...  # type: Any
    published = ...  # type: Any
    rights = ...  # type: Any
    links = ...  # type: Any
    categories = ...  # type: Any
    xml_base = ...  # type: Any
    def __init__(self, title=None, content=None, feed_url=None, **kwargs): ...
    def generate(self): ...
    def to_string(self): ...
