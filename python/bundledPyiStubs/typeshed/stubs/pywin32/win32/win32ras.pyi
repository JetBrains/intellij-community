from _typeshed import Incomplete
from collections.abc import Callable, Iterable
from typing import Final, Literal

import _win32typing
from win32.lib.pywintypes import error as error

def CreatePhonebookEntry(hWnd: int | _win32typing.PyHANDLE | None, fileName: str = ..., /) -> None: ...
def Dial(
    dialExtensions: _win32typing.PyRASDIALEXTENSIONS | None,
    fileName: str | None,
    RasDialParams: Iterable[str],
    callback: int | Callable[..., Incomplete] | None,
    /,
) -> tuple[int, int]: ...
def EditPhonebookEntry(hWnd: int | _win32typing.PyHANDLE | None, fileName: str | None, entryName: str, /) -> None: ...
def EnumConnections() -> list[tuple[int, str, str, str]]: ...
def EnumEntries(reserved: str | None = None, fileName: str | None = None, /) -> tuple[str, ...]: ...
def GetConnectStatus(hrasconn: int | _win32typing.PyHANDLE | None, /) -> tuple[int, int, str, str]: ...
def GetEapUserIdentity(
    phoneBook: str | None, entry: str, flags: int, hwnd: int | _win32typing.PyHANDLE | None = None, /
) -> _win32typing.PyRASEAPUSERIDENTITY | None: ...
def GetEntryDialParams(fileName: str | None, entryName: str, /) -> tuple[tuple[str, str, str, str, str, str], bool]: ...
def GetErrorString(error: int, /) -> str: ...
def HangUp(hras: int | _win32typing.PyHANDLE | None, /) -> None: ...
def IsHandleValid(hras: int | _win32typing.PyHANDLE | None, /) -> bool: ...
def SetEntryDialParams(fileName: str | None, RasDialParams: Iterable[str], bSavePassword: bool | Literal[0, 1], /) -> None: ...
def RASDIALEXTENSIONS() -> _win32typing.PyRASDIALEXTENSIONS: ...

RASCS_OpenPort: Final[int]
RASCS_PortOpened: Final[int]
RASCS_ConnectDevice: Final[int]
RASCS_DeviceConnected: Final[int]
RASCS_AllDevicesConnected: Final[int]
RASCS_Authenticate: Final[int]
RASCS_AuthNotify: Final[int]
RASCS_AuthRetry: Final[int]
RASCS_AuthCallback: Final[int]
RASCS_AuthChangePassword: Final[int]
RASCS_AuthProject: Final[int]
RASCS_AuthLinkSpeed: Final[int]
RASCS_AuthAck: Final[int]
RASCS_ReAuthenticate: Final[int]
RASCS_Authenticated: Final[int]
RASCS_PrepareForCallback: Final[int]
RASCS_WaitForModemReset: Final[int]
RASCS_WaitForCallback: Final[int]
RASCS_Projected: Final[int]
RASCS_StartAuthentication: Final[int]
RASCS_CallbackComplete: Final[int]
RASCS_LogonNetwork: Final[int]
RASCS_Interactive: Final[int]
RASCS_RetryAuthentication: Final[int]
RASCS_CallbackSetByCaller: Final[int]
RASCS_PasswordExpired: Final[int]
RASCS_Connected: Final[int]
RASCS_Disconnected: Final[int]
RASEAPF_NonInteractive: Final[int]
RASEAPF_Logon: Final[int]
RASEAPF_Preview: Final[int]
