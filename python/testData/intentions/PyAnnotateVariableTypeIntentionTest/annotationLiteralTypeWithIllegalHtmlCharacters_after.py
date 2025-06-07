from typing import Literal, Set, Union

HTML_ESCAPES: set[Literal["<", ">", "&"]] = {"<", ">", "&"}
var: [Set[Literal["<", ">", "&"]]] = HTML_ESCAPES