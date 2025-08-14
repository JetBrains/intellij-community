import datetime
from collections.abc import Sequence
from typing import Any

from django.utils.xmlutils import SimplerXMLGenerator
from typing_extensions import TypeAlias

def rfc2822_date(date: datetime.date) -> str: ...
def rfc3339_date(date: datetime.date) -> str: ...
def get_tag_uri(url: str, date: datetime.date | None) -> str: ...

class SyndicationFeed:
    feed: dict[str, Any]
    items: list[dict[str, Any]]
    def __init__(
        self,
        title: str,
        link: str,
        description: str | None,
        language: str | None = ...,
        author_email: str | None = ...,
        author_name: str | None = ...,
        author_link: str | None = ...,
        subtitle: str | None = ...,
        categories: tuple[str, str] | None = ...,
        feed_url: str | None = ...,
        feed_copyright: str | None = ...,
        feed_guid: str | None = ...,
        ttl: int | None = ...,
        **kwargs: Any,
    ) -> None: ...
    def add_item(
        self,
        title: str,
        link: str,
        description: str,
        author_email: str | None = ...,
        author_name: str | None = ...,
        author_link: str | None = ...,
        pubdate: datetime.datetime | None = ...,
        comments: str | None = ...,
        unique_id: str | None = ...,
        unique_id_is_permalink: bool | None = ...,
        categories: Sequence[str | None] | None = ...,
        item_copyright: str | None = ...,
        ttl: int | None = ...,
        updateddate: datetime.datetime | None = ...,
        enclosures: list[Enclosure] | None = ...,
        **kwargs: Any,
    ) -> None: ...
    def num_items(self) -> int: ...
    def root_attributes(self) -> dict[Any, Any]: ...
    def add_root_elements(self, handler: SimplerXMLGenerator) -> None: ...
    def item_attributes(self, item: dict[str, Any]) -> dict[Any, Any]: ...
    def add_item_elements(
        self,
        handler: SimplerXMLGenerator,
        item: dict[str, Any],
    ) -> None: ...
    def write(self, outfile: Any, encoding: Any) -> None: ...
    def writeString(self, encoding: str) -> str: ...
    def latest_post_date(self) -> datetime.datetime: ...

class Enclosure:
    length: Any
    mime_type: str
    url: str
    def __init__(self, url: str, length: int | str, mime_type: str) -> None: ...

class RssFeed(SyndicationFeed):
    content_type: str
    def rss_attributes(self) -> dict[str, str]: ...
    def write_items(self, handler: SimplerXMLGenerator) -> None: ...
    def endChannelElement(self, handler: SimplerXMLGenerator) -> None: ...

class RssUserland091Feed(RssFeed): ...
class Rss201rev2Feed(RssFeed): ...

class Atom1Feed(SyndicationFeed):
    content_type: str
    ns: str
    def write_items(self, handler: SimplerXMLGenerator) -> None: ...

DefaultFeed: TypeAlias = Rss201rev2Feed
