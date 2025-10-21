prefix: str | None
localName: str
namespace: str
prefix_mapping: dict[str, str]

def to_sax(walker, handler) -> None: ...
