from typing import Literal

HTML_ESCAPES: set[Literal["<", ">", "&"]] = {"<", ">", "&"}
var: [set[Literal["<", ">", "&"]]] = HTML_ESCAPES