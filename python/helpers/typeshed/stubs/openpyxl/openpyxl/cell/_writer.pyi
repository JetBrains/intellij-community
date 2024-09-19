from _typeshed import Incomplete, Unused

def etree_write_cell(xf, worksheet: Unused, cell, styled: Incomplete | None = None) -> None: ...
def lxml_write_cell(xf, worksheet: Unused, cell, styled: bool = False) -> None: ...

write_cell = lxml_write_cell
write_cell = etree_write_cell
