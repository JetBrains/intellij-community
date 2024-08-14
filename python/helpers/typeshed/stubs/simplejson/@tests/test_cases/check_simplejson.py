from __future__ import annotations

from typing_extensions import assert_type

from simplejson import JSONEncoder, dumps


class CustomEncoder(JSONEncoder):  # eventhough it does not have `extra_kw` arg.
    ...


# We are only testing `dumps` here, because they are all the same:
dumps([], extra_kw=True)  # type: ignore

# Ok:
assert_type(dumps([], cls=CustomEncoder, extra_kw=True), str)
