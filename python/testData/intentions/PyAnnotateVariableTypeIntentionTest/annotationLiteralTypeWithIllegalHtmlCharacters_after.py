from typing import Literal, Set

HTML_ESCAPES: set[Literal["<", ">", "&"]] = {"<", ">", "&"}
var: [Set[Literal["<", ">", "&"]]] = HTML_ESCAPES