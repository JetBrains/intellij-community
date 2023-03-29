import ctypes
from ctypes import wintypes

class DISPLAY_DEVICE(ctypes.Structure):
    cb: wintypes.DWORD
    DeviceName: wintypes.WCHAR
    DeviceString: wintypes.WCHAR
    StateFlags: wintypes.DWORD
    DeviceID: wintypes.WCHAR
    DeviceKey: wintypes.WCHAR

def get_display_device_name_mapping() -> dict[str, tuple[str, bool]]: ...
def get_hmonitor_by_point(x: wintypes.LONG, y: wintypes.LONG) -> wintypes.HMONITOR: ...
