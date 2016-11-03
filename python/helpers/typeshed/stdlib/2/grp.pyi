from typing import Optional, List

class struct_group(object):
    gr_name = ...  # type: Optional[str]
    gr_passwd = ...  # type: Optional[str]
    gr_gid = ...  # type: int
    gr_mem = ...  # type: List[str]

def getgrall() -> List[struct_group]: ...
def getgrgid(id: int) -> struct_group: ...
def getgrnam(name: str) -> struct_group: ...
