# pyright: reportUnnecessaryTypeIgnoreComment=true

import requests

# Regression test for #7988 (multiple files should be allowed for the "files" argument)
# This snippet comes from the requests documentation (https://requests.readthedocs.io/en/latest/user/advanced/#post-multiple-multipart-encoded-files),
# so should pass a type checker without error
url = "https://httpbin.org/post"
multiple_files = [
    ("images", ("foo.png", open("foo.png", "rb"), "image/png")),
    ("images", ("bar.png", open("bar.png", "rb"), "image/png")),
]
r = requests.post(url, files=multiple_files)
