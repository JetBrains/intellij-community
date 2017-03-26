from typing import List

class struct_spwd(object):
    sp_nam = ...  # type: str
    sp_pwd = ...  # type: str
    sp_lstchg = ...  # type: int
    sp_min = ...  # type: int
    sp_max = ...  # type: int
    sp_warn = ...  # type: int
    sp_inact = ...  # type: int
    sp_expire = ...  # type: int
    sp_flag = ...  # type: int

def getspall() -> List[struct_spwd]: pass
def getspnam() -> struct_spwd: pass
