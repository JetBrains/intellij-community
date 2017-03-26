# Stubs for pwd

# NOTE: These are incomplete!

import typing

class struct_passwd:
    # TODO use namedtuple
    pw_name = ...  # type: str
    pw_passwd = ...  # type: str
    pw_uid = 0
    pw_gid = 0
    pw_gecos = ...  # type: str
    pw_dir = ...  # type: str
    pw_shell = ...  # type: str

def getpwuid(uid: int) -> struct_passwd: ...
def getpwnam(name: str) -> struct_passwd: ...
