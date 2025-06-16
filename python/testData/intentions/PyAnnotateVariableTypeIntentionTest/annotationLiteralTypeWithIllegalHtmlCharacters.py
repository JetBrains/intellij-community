from typing import Literal

HTML_ESCAPES: set[Literal["<", ">", "&"]] = {"<", ">", "&"}
v<caret>ar = HTML_ESCAPES