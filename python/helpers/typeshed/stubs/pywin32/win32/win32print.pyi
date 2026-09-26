from typing import Final, Literal, overload

import _win32typing

def OpenPrinter(printer: str, Defaults: _win32typing.PrinterDefaults | None = None, /) -> _win32typing.PyPrinterHANDLE: ...

@overload
def GetPrinter(hPrinter: _win32typing.PyPrinterHANDLE, /) -> _win32typing.PrinterInfo2Tuple: ...
@overload
def GetPrinter(hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[1], /) -> _win32typing.PrinterInfo1: ...
@overload
def GetPrinter(hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[2], /) -> _win32typing.PrinterInfo2: ...
@overload
def GetPrinter(hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[3], /) -> _win32typing.PrinterInfo3: ...
@overload
def GetPrinter(hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[4], /) -> _win32typing.PrinterInfo4: ...
@overload
def GetPrinter(hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[5], /) -> _win32typing.PrinterInfo5: ...
@overload
def GetPrinter(hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[6], /) -> _win32typing.PrinterInfo6: ...
@overload
def GetPrinter(hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[7], /) -> _win32typing.PrinterInfo7: ...
@overload
def GetPrinter(hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[8, 9], /) -> _win32typing.PrinterInfo89: ...

@overload
def SetPrinter(hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[0], pPrinter: int | None, Command: int, /) -> None: ...
@overload
def SetPrinter(
    hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[2], pPrinter: _win32typing.PrinterInfo2, Command: Literal[0], /
) -> None: ...
@overload
def SetPrinter(
    hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[3], pPrinter: _win32typing.PrinterInfo3, Command: Literal[0], /
) -> None: ...
@overload
def SetPrinter(
    hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[4], pPrinter: _win32typing.PrinterInfo4, Command: Literal[0], /
) -> None: ...
@overload
def SetPrinter(
    hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[5], pPrinter: _win32typing.PrinterInfo5, Command: Literal[0], /
) -> None: ...
@overload
def SetPrinter(
    hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[6], pPrinter: _win32typing.PrinterInfo6, Command: Literal[0], /
) -> None: ...
@overload
def SetPrinter(
    hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[7], pPrinter: _win32typing.PrinterInfo7, Command: Literal[0], /
) -> None: ...
@overload
def SetPrinter(
    hPrinter: _win32typing.PyPrinterHANDLE, Level: Literal[8, 9], pPrinter: _win32typing.PrinterInfo89, Command: Literal[0], /
) -> None: ...

def ClosePrinter(hPrinter: _win32typing.PyPrinterHANDLE, /) -> None: ...
def AddPrinterConnection(printer: str, /) -> None: ...
def DeletePrinterConnection(printer: str, /) -> None: ...

@overload
def EnumPrinters(flags: int, name: str | None = None, level: Literal[1] = 1, /) -> tuple[_win32typing.PrinterInfo1Tuple, ...]: ...
@overload
def EnumPrinters(flags: int, name: str | None, level: Literal[2], /) -> tuple[_win32typing.PrinterInfo2, ...]: ...
@overload
def EnumPrinters(flags: int, name: str | None, level: Literal[4], /) -> tuple[_win32typing.PrinterInfo4, ...]: ...
@overload
def EnumPrinters(flags: int, name: str | None, level: Literal[5], /) -> tuple[_win32typing.PrinterInfo5, ...]: ...

def GetDefaultPrinter() -> str: ...
def GetDefaultPrinterW() -> str: ...
def SetDefaultPrinter(printer: str, /) -> None: ...
def SetDefaultPrinterW(Printer: str | None, /) -> None: ...
def StartDocPrinter(
    hprinter: _win32typing.PyPrinterHANDLE, level: Literal[1], tuple: tuple[str, str | None, str | None], /
) -> int: ...
def EndDocPrinter(hPrinter: _win32typing.PyPrinterHANDLE, /) -> None: ...
def AbortPrinter(hPrinter: _win32typing.PyPrinterHANDLE, /) -> None: ...
def StartPagePrinter(hprinter: _win32typing.PyPrinterHANDLE, /) -> None: ...
def EndPagePrinter(hprinter: _win32typing.PyPrinterHANDLE, /) -> None: ...
def StartDoc(hdc: int, docinfo: tuple[str, str | None, str | None, int], /) -> int: ...
def EndDoc(hdc: int, /) -> None: ...
def AbortDoc(hdc: int, /) -> None: ...
def StartPage(hdc: int, /) -> None: ...
def EndPage(hdc: int, /) -> None: ...
def WritePrinter(hprinter: _win32typing.PyPrinterHANDLE, buf: bytes | bytearray | memoryview, /) -> int: ...

@overload
def EnumJobs(
    hPrinter: _win32typing.PyPrinterHANDLE, FirstJob: int, NoJobs: int, Level: Literal[1] = 1, /
) -> tuple[_win32typing.JobInfo1, ...]: ...
@overload
def EnumJobs(
    hPrinter: _win32typing.PyPrinterHANDLE, FirstJob: int, NoJobs: int, Level: Literal[2], /
) -> tuple[_win32typing.JobInfo2, ...]: ...
@overload
def EnumJobs(
    hPrinter: _win32typing.PyPrinterHANDLE, FirstJob: int, NoJobs: int, Level: Literal[3], /
) -> tuple[_win32typing.JobInfo3, ...]: ...

@overload
def GetJob(hPrinter: _win32typing.PyPrinterHANDLE, JobID: int, Level: Literal[1] = 1, /) -> _win32typing.JobInfo1: ...
@overload
def GetJob(hPrinter: _win32typing.PyPrinterHANDLE, JobID: int, Level: Literal[2], /) -> _win32typing.JobInfo2: ...
@overload
def GetJob(hPrinter: _win32typing.PyPrinterHANDLE, JobID: int, Level: Literal[3], /) -> _win32typing.JobInfo3: ...

@overload
def SetJob(hPrinter: _win32typing.PyPrinterHANDLE, JobID: int, Level: Literal[0], JobInfo: None, Command: int, /) -> None: ...
@overload
def SetJob(
    hPrinter: _win32typing.PyPrinterHANDLE, JobID: int, Level: Literal[1], JobInfo: _win32typing.JobInfo1, Command: int, /
) -> None: ...
@overload
def SetJob(
    hPrinter: _win32typing.PyPrinterHANDLE, JobID: int, Level: Literal[2], JobInfo: _win32typing.JobInfo2, Command: int, /
) -> None: ...
@overload
def SetJob(
    hPrinter: _win32typing.PyPrinterHANDLE, JobID: int, Level: Literal[3], JobInfo: _win32typing.JobInfo3, Command: int, /
) -> None: ...

def DocumentProperties(
    HWnd: int,
    hPrinter: _win32typing.PyPrinterHANDLE,
    DeviceName: str,
    DevModeOutput: _win32typing.PyDEVMODEW,
    DevModeInput: _win32typing.PyDEVMODEW,
    Mode: int,
    /,
) -> int: ...
def EnumPrintProcessors(Server: str | None = None, Environment: str | None = None, /) -> tuple[str, ...]: ...
def EnumPrintProcessorDatatypes(ServerName: str | None, PrintProcessorName: str, /) -> tuple[str, ...]: ...

@overload
def EnumPrinterDrivers(
    Server: str | None = None, Environment: str | None = None, Level: Literal[1] = 1, /
) -> tuple[_win32typing.DriverInfo1, ...]: ...
@overload
def EnumPrinterDrivers(
    Server: str | None, Environment: str | None, Level: Literal[2], /
) -> tuple[_win32typing.DriverInfo2, ...]: ...
@overload
def EnumPrinterDrivers(
    Server: str | None, Environment: str | None, Level: Literal[3], /
) -> tuple[_win32typing.DriverInfo3, ...]: ...
@overload
def EnumPrinterDrivers(
    Server: str | None, Environment: str | None, Level: Literal[4], /
) -> tuple[_win32typing.DriverInfo4, ...]: ...
@overload
def EnumPrinterDrivers(
    Server: str | None, Environment: str | None, Level: Literal[5], /
) -> tuple[_win32typing.DriverInfo5, ...]: ...
@overload
def EnumPrinterDrivers(
    Server: str | None, Environment: str | None, Level: Literal[6], /
) -> tuple[_win32typing.DriverInfo6, ...]: ...

def EnumForms(hprinter: _win32typing.PyPrinterHANDLE, /) -> tuple[_win32typing.FormInfo1, ...]: ...
def AddForm(hprinter: _win32typing.PyPrinterHANDLE, Form: _win32typing.FormInfo1, /) -> None: ...
def DeleteForm(hprinter: _win32typing.PyPrinterHANDLE, FormName: str, /) -> None: ...
def GetForm(hprinter: _win32typing.PyPrinterHANDLE, FormName: str, /) -> _win32typing.FormInfo1: ...
def SetForm(hprinter: _win32typing.PyPrinterHANDLE, FormName: str, Form: _win32typing.FormInfo1, /) -> None: ...
def AddJob(hprinter: _win32typing.PyPrinterHANDLE, /) -> tuple[str, int]: ...
def ScheduleJob(hprinter: _win32typing.PyPrinterHANDLE, JobId: int, /) -> None: ...

@overload
# DC_MINEXTENT, DC_MAXEXTENT
def DeviceCapabilities(  # type: ignore[overload-overlap]
    Device: str, Port: str, Capability: Literal[4, 5], DEVMODE: _win32typing.PyDEVMODEW | None = None, /
) -> _win32typing.PrinterExtents: ...
@overload
# DC_ENUMRESOLUTIONS
def DeviceCapabilities(  # type: ignore[overload-overlap]
    Device: str, Port: str, Capability: Literal[13], DEVMODE: _win32typing.PyDEVMODEW | None = None, /
) -> tuple[_win32typing.PrinterDpi, ...]: ...
@overload
# DC_PAPERS, DC_BINS, DC_NUP, DC_MEDIATYPES
def DeviceCapabilities(  # type: ignore[overload-overlap]
    Device: str, Port: str, Capability: Literal[2, 6, 33, 35], DEVMODE: _win32typing.PyDEVMODEW | None = None, /
) -> tuple[int, ...]: ...
@overload
# DC_BINNAMES, DC_FILEDEPENDENCIES, DC_PAPERNAMES, DC_PERSONALITY, DC_MEDIAREADY, DC_MEDIATYPENAMES
def DeviceCapabilities(  # type: ignore[overload-overlap]
    Device: str, Port: str, Capability: Literal[12, 14, 16, 25, 29, 34], DEVMODE: _win32typing.PyDEVMODEW | None = None, /
) -> tuple[str, ...]: ...
@overload
# DC_PAPERSIZE
def DeviceCapabilities(  # type: ignore[overload-overlap]
    Device: str, Port: str, Capability: Literal[3], DEVMODE: _win32typing.PyDEVMODEW | None = None, /
) -> tuple[_win32typing.PrinterPaperSize, ...]: ...
@overload
# DC_FIELDS, DC_DUPLEX, DC_SIZE, DC_EXTRA, DC_VERSION, DC_DRIVER, DC_TRUETYPE, DC_ORIENTATION, DC_COPIES
# DC_COLLATE, DC_PRINTRATE, DC_PRINTRATEUNIT, DC_PRINTERMEM, DC_STAPLE, DC_PRINTRATEPPM, DC_COLORDEVICE
def DeviceCapabilities(  # type: ignore[overload-overlap]
    Device: str,
    Port: str,
    Capability: Literal[1, 7, 8, 9, 10, 11, 15, 17, 18, 22, 26, 27, 28, 30, 31, 32],
    DEVMODE: _win32typing.PyDEVMODEW | None = None,
    /,
) -> int: ...
@overload
def DeviceCapabilities(Device: str, Port: str, Capability: int, DEVMODE: _win32typing.PyDEVMODEW | None = None, /) -> int: ...

def GetDeviceCaps(hdc: int | _win32typing.PyHANDLE, Index: int, /) -> int: ...

@overload
def EnumMonitors(Name: str | None, Level: Literal[1], /) -> tuple[_win32typing.MonitorInfo1, ...]: ...
@overload
def EnumMonitors(Name: str | None, Level: Literal[2], /) -> tuple[_win32typing.MonitorInfo2, ...]: ...

@overload
def EnumPorts(Name: str | None, Level: Literal[1], /) -> tuple[_win32typing.PortInfo1, ...]: ...
@overload
def EnumPorts(Name: str | None, Level: Literal[2], /) -> tuple[_win32typing.PortInfo2, ...]: ...

def GetPrintProcessorDirectory(Name: str | None = None, Environment: str | None = None, /) -> str: ...
def GetPrinterDriverDirectory(Name: str | None = None, Environment: str | None = None, /) -> str: ...
def AddPrinter(Name: str | None, Level: Literal[2], pPrinter: _win32typing.PrinterInfo2, /) -> _win32typing.PyPrinterHANDLE: ...
def DeletePrinter(hPrinter: _win32typing.PyPrinterHANDLE, /) -> None: ...
def DeletePrinterDriver(Server: str | None, Environment: str | None, DriverName: str, /) -> None: ...
def DeletePrinterDriverEx(
    Server: str | None, Environment: str | None, DriverName: str, DeleteFlag: int, VersionFlag: Literal[0, 1, 2, 3], /
) -> None: ...
def FlushPrinter(Printer: _win32typing.PyPrinterHANDLE, Buf: bytes, Sleep: int, /) -> int: ...

DEF_PRIORITY: int
DI_APPBANDING: int
DI_ROPS_READ_DESTINATION: int
DPD_DELETE_ALL_FILES: int
DPD_DELETE_SPECIFIC_VERSION: int
DPD_DELETE_UNUSED_FILES: int
DSPRINT_PENDING: int
DSPRINT_PUBLISH: int
DSPRINT_REPUBLISH: int
DSPRINT_UNPUBLISH: int
DSPRINT_UPDATE: int
FORM_BUILTIN: int
FORM_PRINTER: int
FORM_USER: int
JOB_ACCESS_ADMINISTER: int
JOB_ACCESS_READ: int
JOB_ALL_ACCESS: int
JOB_CONTROL_CANCEL: int
JOB_CONTROL_DELETE: int
JOB_CONTROL_LAST_PAGE_EJECTED: int
JOB_CONTROL_PAUSE: int
JOB_CONTROL_RESTART: int
JOB_CONTROL_RESUME: int
JOB_CONTROL_SENT_TO_PRINTER: int
JOB_EXECUTE: int
JOB_INFO_1: int
JOB_POSITION_UNSPECIFIED: int
JOB_READ: int
JOB_STATUS_BLOCKED_DEVQ: int
JOB_STATUS_COMPLETE: int
JOB_STATUS_DELETED: int
JOB_STATUS_DELETING: int
JOB_STATUS_ERROR: int
JOB_STATUS_OFFLINE: int
JOB_STATUS_PAPEROUT: int
JOB_STATUS_PAUSED: int
JOB_STATUS_PRINTED: int
JOB_STATUS_PRINTING: int
JOB_STATUS_RESTART: int
JOB_STATUS_SPOOLING: int
JOB_STATUS_USER_INTERVENTION: int
JOB_WRITE: int
MAX_PRIORITY: int
MIN_PRIORITY: int
PORT_STATUS_DOOR_OPEN: int
PORT_STATUS_NO_TONER: int
PORT_STATUS_OFFLINE: int
PORT_STATUS_OUTPUT_BIN_FULL: int
PORT_STATUS_OUT_OF_MEMORY: int
PORT_STATUS_PAPER_JAM: int
PORT_STATUS_PAPER_OUT: int
PORT_STATUS_PAPER_PROBLEM: int
PORT_STATUS_POWER_SAVE: int
PORT_STATUS_TONER_LOW: int
PORT_STATUS_TYPE_ERROR: int
PORT_STATUS_TYPE_INFO: int
PORT_STATUS_TYPE_WARNING: int
PORT_STATUS_USER_INTERVENTION: int
PORT_STATUS_WARMING_UP: int
PORT_TYPE_NET_ATTACHED: int
PORT_TYPE_READ: int
PORT_TYPE_REDIRECTED: int
PORT_TYPE_WRITE: int
PRINTER_ACCESS_ADMINISTER: int
PRINTER_ACCESS_USE: int
PRINTER_ALL_ACCESS: int
PRINTER_ATTRIBUTE_DEFAULT: int
PRINTER_ATTRIBUTE_DIRECT: int
PRINTER_ATTRIBUTE_DO_COMPLETE_FIRST: int
PRINTER_ATTRIBUTE_ENABLE_BIDI: int
PRINTER_ATTRIBUTE_ENABLE_DEVQ: int
PRINTER_ATTRIBUTE_FAX: int
PRINTER_ATTRIBUTE_HIDDEN: int
PRINTER_ATTRIBUTE_KEEPPRINTEDJOBS: int
PRINTER_ATTRIBUTE_LOCAL: int
PRINTER_ATTRIBUTE_NETWORK: int
PRINTER_ATTRIBUTE_PUBLISHED: int
PRINTER_ATTRIBUTE_QUEUED: int
PRINTER_ATTRIBUTE_RAW_ONLY: int
PRINTER_ATTRIBUTE_SHARED: int
PRINTER_ATTRIBUTE_TS: int
PRINTER_ATTRIBUTE_WORK_OFFLINE: int
PRINTER_CONTROL_PAUSE: int
PRINTER_CONTROL_PURGE: int
PRINTER_CONTROL_RESUME: int
PRINTER_CONTROL_SET_STATUS: int
PRINTER_ENUM_CONNECTIONS: int
PRINTER_ENUM_CONTAINER: int
PRINTER_ENUM_DEFAULT: int
PRINTER_ENUM_EXPAND: int
PRINTER_ENUM_ICON1: int
PRINTER_ENUM_ICON2: int
PRINTER_ENUM_ICON3: int
PRINTER_ENUM_ICON4: int
PRINTER_ENUM_ICON5: int
PRINTER_ENUM_ICON6: int
PRINTER_ENUM_ICON7: int
PRINTER_ENUM_ICON8: int
PRINTER_ENUM_LOCAL: int
PRINTER_ENUM_NAME: int
PRINTER_ENUM_NETWORK: int
PRINTER_ENUM_REMOTE: int
PRINTER_ENUM_SHARED: int
PRINTER_EXECUTE: int
PRINTER_INFO_1: Final = 1
PRINTER_READ: int
PRINTER_STATUS_BUSY: int
PRINTER_STATUS_DOOR_OPEN: int
PRINTER_STATUS_ERROR: int
PRINTER_STATUS_INITIALIZING: int
PRINTER_STATUS_IO_ACTIVE: int
PRINTER_STATUS_MANUAL_FEED: int
PRINTER_STATUS_NOT_AVAILABLE: int
PRINTER_STATUS_NO_TONER: int
PRINTER_STATUS_OFFLINE: int
PRINTER_STATUS_OUTPUT_BIN_FULL: int
PRINTER_STATUS_OUT_OF_MEMORY: int
PRINTER_STATUS_PAGE_PUNT: int
PRINTER_STATUS_PAPER_JAM: int
PRINTER_STATUS_PAPER_OUT: int
PRINTER_STATUS_PAPER_PROBLEM: int
PRINTER_STATUS_PAUSED: int
PRINTER_STATUS_PENDING_DELETION: int
PRINTER_STATUS_POWER_SAVE: int
PRINTER_STATUS_PRINTING: int
PRINTER_STATUS_PROCESSING: int
PRINTER_STATUS_SERVER_UNKNOWN: int
PRINTER_STATUS_TONER_LOW: int
PRINTER_STATUS_USER_INTERVENTION: int
PRINTER_STATUS_WAITING: int
PRINTER_STATUS_WARMING_UP: int
PRINTER_WRITE: int
SERVER_ACCESS_ADMINISTER: int
SERVER_ACCESS_ENUMERATE: int
SERVER_ALL_ACCESS: int
SERVER_EXECUTE: int
SERVER_READ: int
SERVER_WRITE: int
