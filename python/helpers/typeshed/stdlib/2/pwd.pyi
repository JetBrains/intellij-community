from typing import List

class struct_passwd(tuple):
    n_fields = ...  # type: int
    n_sequence_fields = ...  # type: int
    n_unnamed_fields = ...  # type: int
    pw_dir = ...  # type: str
    pw_name = ...  # type: str
    pw_passwd = ...  # type: str
    pw_shell = ...  # type: str
    pw_gecos = ...  # type: str
    pw_gid = ...  # type: int
    pw_uid = ...  # type: int

def getpwall() -> List[struct_passwd]: ...
def getpwnam(name:str) -> struct_passwd: ...
def getpwuid(uid:int) -> struct_passwd: ...

