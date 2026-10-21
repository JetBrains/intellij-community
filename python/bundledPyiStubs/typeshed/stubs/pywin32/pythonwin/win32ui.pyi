from _typeshed import FileDescriptorOrPath, Incomplete, OptExcInfo, Unused
from collections.abc import Callable, Iterable, Mapping, Sequence
from types import TracebackType
from typing import Final, Literal, overload

import _win32typing

class error(Exception): ...

def ComparePath(path1: str, path2: str, /) -> int: ...
def CreateMDIFrame() -> _win32typing.PyCMDIFrameWnd: ...
def CreateMDIChild() -> _win32typing.PyCMDIChildWnd: ...
def CreateBitmap(*args: Unused) -> _win32typing.PyCBitmap: ...
def CreateBitmapFromHandle(handle: int | _win32typing.PyHANDLE | None, /) -> _win32typing.PyCBitmap: ...

@overload
def CreateBrush() -> _win32typing.PyCBrush: ...
@overload
def CreateBrush(style: int, color: int, hatch: int, /) -> _win32typing.PyCBrush: ...

def CreateButton() -> _win32typing.PyCButton: ...
def CreateColorDialog(
    initColor: int = 0, flags: int = 0, parent: _win32typing.PyCWnd | None = None, /
) -> _win32typing.PyCColorDialog: ...
def CreateControl(
    classId: str,
    windowName: str | None,
    style: int,
    rect: tuple[int, int, int, int],
    parent: _win32typing.PyCWnd,
    id: int,
    obPersist: Unused = None,
    bStorage: bool | Literal[0, 1] = False,
    licKey: str | None = None,
    /,
) -> _win32typing.PyCWnd: ...
def CreateControlBar() -> _win32typing.PyCControlBar: ...
def CreateCtrlView(doc: _win32typing.PyCDocument, className: str, style: int = 0, /) -> _win32typing.PyCCtrlView: ...
def CreateDC() -> _win32typing.PyCDC: ...
def CreateDCFromHandle(handle: int | _win32typing.PyHANDLE | None, /) -> _win32typing.PyCDC: ...
def CreateDialog(idRes: int, dll: _win32typing.PyDLL | None = None, /) -> _win32typing.PyCDialog: ...
def CreateDialogBar() -> _win32typing.PyCDialogBar: ...
def CreateDialogIndirect(
    oblist: Iterable[_win32typing.PyDLGTEMPLATE | _win32typing.PyDLGITEMTEMPLATE], /
) -> _win32typing.PyCDialog: ...
def CreatePrintDialog(
    idRes: int,
    bPrintSetupOnly: bool | Literal[0, 1] = False,
    dwFlags: int = ...,
    parent: _win32typing.PyCWnd | None = None,
    dll: _win32typing.PyDLL | None = None,
    /,
) -> _win32typing.PyCPrintDialog: ...
def CreateDocTemplate(idRes: int, /) -> _win32typing.PyCDocTemplate: ...
def CreateEdit() -> _win32typing.PyCEdit: ...
def CreateFileDialog(
    bFileOpen: int,
    defExt: str | None = None,
    fileName: str | None = None,
    flags: int = ...,
    filter: str | None = None,
    parent: _win32typing.PyCWnd | None = None,
    /,
) -> _win32typing.PyCFileDialog: ...
def CreateFontDialog(
    font: tuple[int, int, int, int, int, int, int, str | None] | Mapping[str, int | str | None] | None = None,
    flags: int = ...,
    dcPrinter: _win32typing.PyCDC | None = None,
    parent: _win32typing.PyCWnd | None = None,
    /,
) -> _win32typing.PyCFontDialog: ...
def CreateFormView(
    doc: _win32typing.PyCDocument, Template: int | str | _win32typing.PyResourceId, /
) -> _win32typing.PyCFormView: ...
def CreateFrame() -> _win32typing.PyCFrameWnd: ...
def CreateTreeCtrl(*args: Unused) -> _win32typing.PyCTreeCtrl: ...
def CreateTreeView(doc: _win32typing.PyCDocument, /) -> _win32typing.PyCTreeView: ...
def CreatePalette(lp: Sequence[tuple[int, int, int, int]], /) -> int: ...
def CreatePopupMenu() -> _win32typing.PyCMenu: ...
def CreateMenu() -> _win32typing.PyCMenu: ...
def CreatePen(style: int, width: int, color: int, /): ...
def CreateProgressCtrl() -> _win32typing.PyCProgressCtrl: ...
def CreatePropertyPage(resource: int | str | _win32typing.PyResourceId, caption: int = 0, /) -> _win32typing.PyCPropertyPage: ...
def CreatePropertyPageIndirect(
    resourceList: Iterable[Incomplete] | _win32typing.PyDialogTemplate, caption: int = 0, /
) -> _win32typing.PyCPropertyPage: ...
def CreatePropertySheet(
    caption: int | str | _win32typing.PyResourceId, parent: _win32typing.PyCWnd | None = None, select: int = 0, /
) -> _win32typing.PyCPropertySheet: ...
def CreateRgn() -> _win32typing.PyCRgn: ...
def CreateRichEditCtrl() -> _win32typing.PyCRichEditCtrl: ...
def CreateRichEditDocTemplate(idRes: int, /) -> _win32typing.PyCRichEditDocTemplate: ...
def CreateRichEditView(doc: _win32typing.PyCDocument | None = None, /) -> _win32typing.PyCRichEditView: ...
def CreateSliderCtrl() -> _win32typing.PyCSliderCtrl: ...
def CreateSplitter() -> _win32typing.PyCSplitterWnd: ...
def CreateStatusBar(
    parent: _win32typing.PyCWnd, style: int = ..., windowId: int = ..., ctrlStype: int = 0, /
) -> _win32typing.PyCStatusBar: ...
def CreateStatusBarCtrl() -> _win32typing.PyCStatusBarCtrl: ...
def CreateFont(properties: Mapping[str, int | str | None], pydc: _win32typing.PyCDC = ..., /) -> _win32typing.PyCFont: ...
def CreateToolBar(parent: _win32typing.PyCWnd, style: int, windowId: int = ..., /) -> _win32typing.PyCToolBar: ...
def CreateToolBarCtrl() -> _win32typing.PyCToolBarCtrl: ...
def CreateToolTipCtrl() -> _win32typing.PyCToolTipCtrl: ...

@overload
def CreateThread() -> _win32typing.PyCWinThread: ...
@overload
def CreateThread(func: Callable[..., Incomplete], args=None, /) -> _win32typing.PyCWinThread: ...

def CreateView(doc: _win32typing.PyCDocument, /) -> _win32typing.PyCScrollView: ...
def CreateEditView(doc: _win32typing.PyCDocument, /) -> _win32typing.PyCEditView: ...
def CreateDebuggerThread() -> None: ...
def CreateWindowFromHandle(hwnd: int | _win32typing.PyHANDLE | None, /) -> _win32typing.PyCWnd: ...
def CreateWnd() -> _win32typing.PyCWnd: ...
def DestroyDebuggerThread() -> None: ...
def DoWaitCursor(code: Literal[-1, 0, 1], /) -> None: ...
def DisplayTraceback(exc_info: OptExcInfo, title: str | None = None, /) -> None: ...
def Enable3dControls() -> int: ...
def FindWindow(className: str | None, windowName: str | None, /) -> _win32typing.PyCWnd: ...
def FindWindowEx(
    parentWindow: _win32typing.PyCWnd | None,
    childAfter: _win32typing.PyCWnd | None,
    className: str | None,
    windowName: str | None,
    /,
) -> _win32typing.PyCWnd: ...
def FullPath(path: str, /) -> str: ...
def GetActiveWindow() -> _win32typing.PyCWnd: ...
def GetApp() -> _win32typing.PyCWinApp: ...
def GetAppName() -> str: ...
def GetAppRegistryKey() -> _win32typing.PyHKEY: ...
def GetBytes(address: int, size: int, /) -> bytes: ...
def GetCommandLine() -> str: ...
def GetDeviceCaps(hdc: int | _win32typing.PyHANDLE | None, index: int, /) -> int: ...
def GetFileTitle(fileName: str, /) -> str: ...
def GetFocus() -> _win32typing.PyCWnd: ...
def GetForegroundWindow() -> _win32typing.PyCWnd: ...
def GetHalftoneBrush(*args: Unused) -> _win32typing.PyCBrush: ...
def GetInitialStateRequest() -> int: ...
def GetMainFrame() -> _win32typing.PyCWnd: ...
def GetName() -> str: ...
def GetProfileFileName() -> str: ...

@overload
def GetProfileVal(section: str, entry: str, defValue: str | None, /) -> str: ...
@overload
def GetProfileVal(section: str, entry: str, defValue: int, /) -> int: ...

def GetResource() -> _win32typing.PyDLL: ...
def GetThread() -> _win32typing.PyCWinApp: ...
def GetType(name: str, /): ...
def InitRichEdit() -> None: ...
def InstallCallbackCaller(caller: Callable[..., Incomplete] | None = None, /): ...
def IsDebug() -> Literal[0, 1]: ...
def IsWin32s() -> bool: ...
def IsObject(o: object, /) -> bool: ...
def LoadDialogResource(idRes: int, dll: _win32typing.PyDLL | None = None, /) -> list[Incomplete]: ...
def LoadLibrary(fileName: str, flags: int = 0, /) -> _win32typing.PyDLL: ...
def LoadMenu(id: int, dll: _win32typing.PyDLL | None = None, /) -> _win32typing.PyCMenu: ...
def LoadStdProfileSettings(maxFiles: int = ..., /) -> None: ...
def LoadString(stringId: int, /) -> str: ...
def MessageBox(message: str, title: str | None = None, style: int = 0, /) -> int: ...
def EnableControlContainer() -> None: ...
def PrintTraceback(tb: TracebackType, output: FileDescriptorOrPath, /) -> None: ...
def PumpWaitingMessages(firstMessage: int = 15, lastMessage: int = 15, /) -> Literal[0, 1]: ...
def RegisterWndClass(
    style: int,
    hCursor: int | _win32typing.PyHANDLE | None = 0,
    hBrush: int | _win32typing.PyHANDLE | None = 0,
    hIcon: int | _win32typing.PyHANDLE | None = 0,
    /,
) -> str: ...
def RemoveRecentFile(index: int = 0, /) -> None: ...
def SetAppHelpPath(name: str, /) -> None: ...
def SetAppName(appName: str, /) -> None: ...
def SetCurrentInstanceHandle(newVal: int | _win32typing.PyHANDLE | None, /) -> int: ...
def SetCurrentResourceHandle(newVal: int, /) -> int: ...
def SetDialogBkColor(clrCtlBk: int = ..., clrCtlText: int = ..., /) -> None: ...
def SetProfileFileName(filename: str, /) -> None: ...
def SetRegistryKey(key: str, /) -> None: ...
def SetResource(dll: _win32typing.PyDLL, /) -> _win32typing.PyDLL: ...
def SetStatusText(msg: str, bForce: bool | Literal[0, 1] = False, /) -> None: ...
def StartDebuggerPump() -> None: ...
def StopDebuggerPump() -> None: ...
def TranslateMessage(msg: tuple[int, int, int, int, int, tuple[int, int]]) -> int: ...
def TranslateVirtualKey(vk: int, /) -> bytes | None: ...
def WinHelp(cmd: int, data: str | int, /) -> None: ...
def WriteProfileVal(section: str, entry: str, value: str | int, /) -> int: ...
def AddToRecentFileList(fileName: str, /) -> None: ...

@overload
def CreateImageList(cx: int, cy: int, mask: bool | Literal[0, 1], initial: int, grow: int, /) -> _win32typing.PyCImagelist: ...
@overload
def CreateImageList(
    bitmapId: int | str | _win32typing.PyResourceId, cx: int, grow: int, crMask: int, /
) -> _win32typing.PyCImagelist: ...

def CreateListCtrl(*args: Unused) -> _win32typing.PyClistCtrl: ...
def CreateListView(doc: _win32typing.PyCDocument, /) -> _win32typing.PyClistView: ...
def CreateRectRgn(rect: tuple[int, int, int, int], /) -> Literal[0, 1]: ...
def GetRecentFileList() -> list[str]: ...
def OutputDebug(msg: str, /) -> None: ...
def OutputDebugString(msg: str, /) -> None: ...

AFX_IDW_PANE_FIRST: Final[int]
AFX_IDW_PANE_LAST: Final[int]
AFX_WS_DEFAULT_VIEW: Final[int]
FWS_ADDTOTITLE: Final[int]
FWS_PREFIXTITLE: Final[int]
FWS_SNAPTOBARS: Final[int]
IDD_ABOUTBOX: Final[int]
IDD_DUMMYPROPPAGE: Final[int]
IDD_PROPDEMO1: Final[int]
IDD_PROPDEMO2: Final[int]
IDB_DEBUGGER_HIER: Final[int]
IDB_HIERFOLDERS: Final[int]
IDB_BROWSER_HIER: Final[int]
IDD_GENERAL_STATUS: Final[int]
IDD_LARGE_EDIT: Final[int]
IDD_TREE: Final[int]
IDD_TREE_MB: Final[int]
IDD_RUN_SCRIPT: Final[int]
IDD_PP_EDITOR: Final[int]
IDD_PP_DEBUGGER: Final[int]
IDD_PP_FORMAT: Final[int]
IDD_PP_IDE: Final[int]
IDD_PP_TABS: Final[int]
IDD_PP_TOOLMENU: Final[int]
IDD_SIMPLE_INPUT: Final[int]
IDD_SET_TABSTOPS: Final[int]
IDC_DBG_STEP: Final[int]
IDC_DBG_STEPOUT: Final[int]
IDC_DBG_STEPOVER: Final[int]
IDC_DBG_GO: Final[int]
IDC_DBG_ADD: Final[int]
IDC_DBG_CLEAR: Final[int]
IDC_DBG_CLOSE: Final[int]
IDC_DBG_STACK: Final[int]
IDC_DBG_BREAKPOINTS: Final[int]
IDC_DBG_WATCH: Final[int]
IDC_ABOUT_VERSION: Final[int]
IDC_AUTO_RELOAD: Final[int]
IDC_BUTTON1: Final[int]
IDC_BUTTON2: Final[int]
IDC_BUTTON3: Final[int]
IDC_BUTTON4: Final[int]
IDC_CHECK1: Final[int]
IDC_CHECK2: Final[int]
IDC_CHECK3: Final[int]
IDC_COMBO1: Final[int]
IDC_COMBO2: Final[int]
IDC_EDIT1: Final[int]
IDC_EDIT2: Final[int]
IDC_EDIT3: Final[int]
IDC_EDIT4: Final[int]
IDC_EDIT_TABS: Final[int]
IDC_EDITOR_COLOR: Final[int]
IDC_FOLD_SHOW_LINES: Final[int]
IDC_FOLD_ENABLE: Final[int]
IDC_FOLD_ON_OPEN: Final[int]
IDC_INDENT_SIZE: Final[int]
IDC_KEYBOARD_CONFIG: Final[int]
IDC_MARGIN_LINENUMBER: Final[int]
IDC_MARGIN_FOLD: Final[int]
IDC_MARGIN_MARKER: Final[int]
IDC_LIST1: Final[int]
IDC_PROMPT_TABS: Final[int]
IDC_PROMPT1: Final[int]
IDC_PROMPT2: Final[int]
IDC_PROMPT3: Final[int]
IDC_PROMPT4: Final[int]
IDC_RADIO1: Final[int]
IDC_RADIO2: Final[int]
IDC_RIGHTEDGE_COLUMN: Final[int]
IDC_RIGHTEDGE_ENABLE: Final[int]
IDC_RIGHTEDGE_SAMPLE: Final[int]
IDC_RIGHTEDGE_DEFINE: Final[int]
IDC_TABTIMMY_NONE: Final[int]
IDC_TABTIMMY_IND: Final[int]
IDC_TABTIMMY_BG: Final[int]
IDC_VIEW_WHITESPACE: Final[int]
IDC_VIEW_EOL: Final[int]
IDC_VIEW_INDENTATIONGUIDES: Final[int]
IDC_AUTOCOMPLETE: Final[int]
IDC_CALLTIPS: Final[int]
IDC_SPIN1: Final[int]
IDC_SPIN2: Final[int]
IDC_SPIN3: Final[int]
IDC_TAB_SIZE: Final[int]
IDC_USE_TABS: Final[int]
IDC_USE_SMART_TABS: Final[int]
IDC_VSS_INTEGRATE: Final[int]
ID_INDICATOR_LINENUM: Final[int]
ID_INDICATOR_COLNUM: Final[int]
ID_FILE_NEW: Final[int]
ID_FILE_OPEN: Final[int]
ID_FILE_CLOSE: Final[int]
ID_FILE_RUN: Final[int]
ID_FILE_IMPORT: Final[int]
ID_FILE_LOCATE: Final[int]
ID_FILE_CHECK: Final[int]
ID_FILE_SAVE: Final[int]
ID_FILE_SAVE_AS: Final[int]
ID_FILE_SAVE_ALL: Final[int]
ID_FILE_PAGE_SETUP: Final[int]
ID_FILE_PRINT_SETUP: Final[int]
ID_FILE_PRINT: Final[int]
ID_FILE_PRINT_PREVIEW: Final[int]
ID_HELP_PYTHON: Final[int]
ID_HELP_GUI_REF: Final[int]
ID_HELP_OTHER: Final[int]
ID_APP_ABOUT: Final[int]
ID_APP_EXIT: Final[int]
ID_FILE_MRU_FILE1: Final[int]
ID_FILE_MRU_FILE2: Final[int]
ID_FILE_MRU_FILE3: Final[int]
ID_FILE_MRU_FILE4: Final[int]
ID_VIEW_BROWSE: Final[int]
ID_VIEW_FIXED_FONT: Final[int]
ID_VIEW_INTERACTIVE: Final[int]
ID_VIEW_OPTIONS: Final[int]
ID_VIEW_TOOLBAR_DBG: Final[int]
ID_VIEW_WHITESPACE: Final[int]
ID_VIEW_INDENTATIONGUIDES: Final[int]
ID_VIEW_EOL: Final[int]
ID_VIEW_FOLD_EXPAND: Final[int]
ID_VIEW_FOLD_EXPAND_ALL: Final[int]
ID_VIEW_FOLD_COLLAPSE: Final[int]
ID_VIEW_FOLD_COLLAPSE_ALL: Final[int]
ID_VIEW_FOLD_TOPLEVEL: Final[int]
ID_VIEW_RIGHT_EDGE: Final[int]
ID_NEXT_PANE: Final[int]
ID_PREV_PANE: Final[int]
ID_WINDOW_NEW: Final[int]
ID_WINDOW_ARRANGE: Final[int]
ID_WINDOW_CASCADE: Final[int]
ID_WINDOW_TILE_HORZ: Final[int]
ID_WINDOW_TILE_VERT: Final[int]
ID_WINDOW_SPLIT: Final[int]
ID_EDIT_CLEAR: Final[int]
ID_EDIT_CLEAR_ALL: Final[int]
ID_EDIT_COPY: Final[int]
ID_EDIT_CUT: Final[int]
ID_EDIT_FIND: Final[int]
ID_EDIT_GOTO_LINE: Final[int]
ID_EDIT_PASTE: Final[int]
ID_EDIT_REPEAT: Final[int]
ID_EDIT_REPLACE: Final[int]
ID_EDIT_SELECT_ALL: Final[int]
ID_EDIT_SELECT_BLOCK: Final[int]
ID_EDIT_UNDO: Final[int]
ID_EDIT_REDO: Final[int]
ID_VIEW_TOOLBAR: Final[int]
ID_VIEW_STATUS_BAR: Final[int]
ID_SEPARATOR: Final[int]
IDR_DEBUGGER: Final[int]
IDR_PYTHONTYPE_CNTR_IP: Final[int]
IDR_MAINFRAME: Final[int]
IDR_PYTHONTYPE: Final[int]
IDR_PYTHONCONTYPE: Final[int]
IDR_TEXTTYPE: Final[int]
IDR_CNTR_INPLACE: Final[int]
CDocTemplate_windowTitle: Final[int]
CDocTemplate_docName: Final[int]
CDocTemplate_fileNewName: Final[int]
CDocTemplate_filterName: Final[int]
CDocTemplate_filterExt: Final[int]
CDocTemplate_regFileTypeId: Final[int]
CDocTemplate_regFileTypeName: Final[int]
CDocTemplate_Confidence_noAttempt: Final[int]
CDocTemplate_Confidence_maybeAttemptForeign: Final[int]
CDocTemplate_Confidence_maybeAttemptNative: Final[int]
CDocTemplate_Confidence_yesAttemptForeign: Final[int]
CDocTemplate_Confidence_yesAttemptNative: Final[int]
CDocTemplate_Confidence_yesAlreadyOpen: Final[int]
CRichEditView_WrapNone: Final[int]
CRichEditView_WrapToWindow: Final[int]
CRichEditView_WrapToTargetDevice: Final[int]
PD_ALLPAGES: Final[int]
PD_COLLATE: Final[int]
PD_DISABLEPRINTTOFILE: Final[int]
PD_ENABLEPRINTHOOK: Final[int]
PD_ENABLEPRINTTEMPLATE: Final[int]
PD_ENABLEPRINTTEMPLATEHANDLE: Final[int]
PD_ENABLESETUPHOOK: Final[int]
PD_ENABLESETUPTEMPLATE: Final[int]
PD_ENABLESETUPTEMPLATEHANDLE: Final[int]
PD_HIDEPRINTTOFILE: Final[int]
PD_NONETWORKBUTTON: Final[int]
PD_NOPAGENUMS: Final[int]
PD_NOSELECTION: Final[int]
PD_NOWARNING: Final[int]
PD_PAGENUMS: Final[int]
PD_PRINTSETUP: Final[int]
PD_PRINTTOFILE: Final[int]
PD_RETURNDC: Final[int]
PD_RETURNDEFAULT: Final[int]
PD_RETURNIC: Final[int]
PD_SELECTION: Final[int]
PD_SHOWHELP: Final[int]
PD_USEDEVMODECOPIES: Final[int]
PD_USEDEVMODECOPIESANDCOLLATE: Final[int]
PSWIZB_BACK: Final[int]
PSWIZB_NEXT: Final[int]
PSWIZB_FINISH: Final[int]
PSWIZB_DISABLEDFINISH: Final[int]
MFS_SYNCACTIVE: Final[int]
MFS_4THICKFRAME: Final[int]
MFS_THICKFRAME: Final[int]
MFS_MOVEFRAME: Final[int]
MFS_BLOCKSYSMENU: Final[int]
LM_STRETCH: Final[int]
LM_HORZ: Final[int]
LM_MRUWIDTH: Final[int]
LM_HORZDOCK: Final[int]
LM_VERTDOCK: Final[int]
LM_LENGTHY: Final[int]
LM_COMMIT: Final[int]
debug: Final[int]
copyright: Final[str]
dllhandle: Final[int]
types: Final[dict[str, type]]
