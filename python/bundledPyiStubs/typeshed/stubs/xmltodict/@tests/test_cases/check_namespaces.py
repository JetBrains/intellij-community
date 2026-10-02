from __future__ import annotations

import xmltodict

ns: dict[str, None] = {"http://example.com/": None}
xmltodict.parse("<a/>", namespaces=ns)
