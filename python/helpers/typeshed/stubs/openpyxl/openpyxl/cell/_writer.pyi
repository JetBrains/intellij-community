from typing import Any

def etree_write_cell(xf, worksheet, cell, styled: Any | None = ...) -> None: ...
def lxml_write_cell(xf, worksheet, cell, styled: bool = ...) -> None: ...

write_cell = lxml_write_cell
write_cell = etree_write_cell
