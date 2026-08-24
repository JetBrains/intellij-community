# Can't generate with stubgen because:
# "ImportError: This must be an MFC application - try 'import win32ui' first"
from typing import Final

import _win32typing

class error(Exception): ...

def CreateConversation(Server, /) -> _win32typing.PyDDEConv: ...
def CreateServer() -> _win32typing.PyDDEServer: ...
def CreateServerSystemTopic(): ...
def CreateTopic(name: str, /) -> _win32typing.PyDDETopic: ...
def CreateStringItem(name: str, /) -> _win32typing.PyDDEStringItem: ...

APPCLASS_MONITOR: Final[int]
APPCLASS_STANDARD: Final[int]
APPCMD_CLIENTONLY: Final[int]
APPCMD_FILTERINITS: Final[int]
CBF_FAIL_ALLSVRXACTIONS: Final[int]
CBF_FAIL_ADVISES: Final[int]
CBF_FAIL_CONNECTIONS: Final[int]
CBF_FAIL_EXECUTES: Final[int]
CBF_FAIL_POKES: Final[int]
CBF_FAIL_REQUESTS: Final[int]
CBF_FAIL_SELFCONNECTIONS: Final[int]
CBF_SKIP_ALLNOTIFICATIONS: Final[int]
CBF_SKIP_CONNECT_CONFIRMS: Final[int]
CBF_SKIP_DISCONNECTS: Final[int]
CBF_SKIP_REGISTRATIONS: Final[int]
MF_CALLBACKS: Final[int]
MF_CONV: Final[int]
MF_ERRORS: Final[int]
MF_HSZ_INFO: Final[int]
MF_LINKS: Final[int]
MF_POSTMSGS: Final[int]
MF_SENDMSGS: Final[int]
