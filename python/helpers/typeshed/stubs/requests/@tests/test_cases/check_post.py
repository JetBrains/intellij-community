from __future__ import annotations

from collections.abc import Iterable

import requests

# =================================================================================================
# Regression test for #7988 (multiple files should be allowed for the "files" argument)
# This snippet comes from the requests documentation
# (https://requests.readthedocs.io/en/latest/user/advanced/#post-multiple-multipart-encoded-files),
# so should pass a type checker without error
# =================================================================================================


url = "https://httpbin.org/post"
multiple_files = [
    ("images", ("foo.png", open("foo.png", "rb"), "image/png")),
    ("images", ("bar.png", open("bar.png", "rb"), "image/png")),
]
r = requests.post(url, files=multiple_files)


# =================================================================================
# Tests for various different types being passed into the "data" parameter
# (These all return "Any", so there's not much value in using assert_type here.)
# (Just test that type checkers don't emit an error if it doesn't fail at runtime.)
# =================================================================================


# Arbitrary iterable
def gen() -> Iterable[bytes]:
    yield b"foo"
    yield b"bar"


requests.post("http://httpbin.org/anything", data=gen()).json()["data"]

# bytes
requests.post("http://httpbin.org/anything", data=b"foobar").json()["data"]

# str
requests.post("http://httpbin.org/anything", data="foobar").json()["data"]

# Files
requests.post("http://httpbin.org/anything", data=open("/tmp/foobar", "rb", encoding="UTF-8")).json()["data"]
requests.post("http://httpbin.org/anything", data=open("/tmp/foobar", "r", encoding="UTF-8")).json()["data"]

# Mappings
requests.post("http://httpbin.org/anything", data={b"foo": b"bar"}).json()["form"]
requests.post("http://httpbin.org/anything", data={"foo": "bar"}).json()["form"]

# mappings represented by an list/tuple of key-values pairs
requests.post("http://httpbin.org/anything", data=[(b"foo", b"bar")]).json()["form"]
requests.post("http://httpbin.org/anything", data=[("foo", "bar")]).json()["form"]
requests.post("http://httpbin.org/anything", data=((b"foo", b"bar"),)).json()["form"]
requests.post("http://httpbin.org/anything", data=(("foo", "bar"),)).json()["form"]
