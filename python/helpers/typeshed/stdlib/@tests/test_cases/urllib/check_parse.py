from __future__ import annotations

from urllib.parse import quote, quote_plus, urlencode

urlencode({"a": "b"}, quote_via=quote)
urlencode({b"a": b"b"}, quote_via=quote)
urlencode({"a": b"b"}, quote_via=quote)
urlencode({b"a": "b"}, quote_via=quote)
mixed_dict: dict[str | bytes, str | bytes] = {}
urlencode(mixed_dict, quote_via=quote)

urlencode({"a": "b"}, quote_via=quote_plus)
