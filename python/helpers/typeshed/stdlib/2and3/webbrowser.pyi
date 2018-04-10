import sys
from typing import Any, Optional, Callable, List, Text, Union, Sequence

class Error(Exception): ...

def register(name: Text, klass: Optional[Callable[[], BaseBrowser]], instance: BaseBrowser = ..., update_tryorder: int = ...) -> None: ...
def get(using: Optional[Text] = ...) -> BaseBrowser: ...
def open(url: Text, new: int = ..., autoraise: bool = ...) -> bool: ...
def open_new(url: Text) -> bool: ...
def open_new_tab(url: Text) -> bool: ...

class BaseBrowser:
    args = ...  # type: List[str]
    name = ...  # type: str
    basename = ...  # type: str
    def __init__(self, name: Text = ...) -> None: ...
    def open(self, url: Text, new: int = ..., autoraise: bool = ...) -> bool: ...
    def open_new(self, url: Text) -> bool: ...
    def open_new_tab(self, url: Text) -> bool: ...

class GenericBrowser(BaseBrowser):
    args = ...  # type: List[str]
    name = ...  # type: str
    basename = ...  # type: str
    def __init__(self, name: Union[Text, Sequence[Text]]) -> None: ...
    def open(self, url: Text, new: int = ..., autoraise: bool = ...) -> bool: ...

class BackgroundBrowser(GenericBrowser):
    def open(self, url: Text, new: int = ..., autoraise: bool = ...) -> bool: ...

class UnixBrowser(BaseBrowser):
    raise_opts = ...  # type: List[str]
    background = ...  # type: bool
    redirect_stdout = ...  # type: bool
    remote_args = ...  # type: List[str]
    remote_action = ...  # type: str
    remote_action_newwin = ...  # type: str
    remote_action_newtab = ...  # type: str
    def open(self, url: Text, new: int = ..., autoraise: bool = ...) -> bool: ...

class Mozilla(UnixBrowser):
    raise_opts = ...  # type: List[str]
    remote_args = ...  # type: List[str]
    remote_action = ...  # type: str
    remote_action_newwin = ...  # type: str
    remote_action_newtab = ...  # type: str
    background = ...  # type: bool

class Galeon(UnixBrowser):
    raise_opts = ...  # type: List[str]
    remote_args = ...  # type: List[str]
    remote_action = ...  # type: str
    remote_action_newwin = ...  # type: str
    background = ...  # type: bool

if sys.version_info[:2] == (2, 7) or sys.version_info >= (3, 3):
    class Chrome(UnixBrowser):
        remote_args = ...  # type: List[str]
        remote_action = ...  # type: str
        remote_action_newwin = ...  # type: str
        remote_action_newtab = ...  # type: str
        background = ...  # type: bool

class Opera(UnixBrowser):
    raise_opts = ...  # type: List[str]
    remote_args = ...  # type: List[str]
    remote_action = ...  # type: str
    remote_action_newwin = ...  # type: str
    remote_action_newtab = ...  # type: str
    background = ...  # type: bool

class Elinks(UnixBrowser):
    remote_args = ...  # type: List[str]
    remote_action = ...  # type: str
    remote_action_newwin = ...  # type: str
    remote_action_newtab = ...  # type: str
    background = ...  # type: bool
    redirect_stdout = ...  # type: bool

class Konqueror(BaseBrowser):
    def open(self, url: Text, new: int = ..., autoraise: bool = ...) -> bool: ...

class Grail(BaseBrowser):
    def open(self, url: Text, new: int = ..., autoraise: bool = ...) -> bool: ...

class WindowsDefault(BaseBrowser):
    def open(self, url: Text, new: int = ..., autoraise: bool = ...) -> bool: ...

class MacOSX(BaseBrowser):
    name = ...  # type: str
    def __init__(self, name: Text) -> None: ...
    def open(self, url: Text, new: int = ..., autoraise: bool = ...) -> bool: ...

class MacOSXOSAScript(BaseBrowser):
    def __init__(self, name: Text) -> None: ...
    def open(self, url: Text, new: int = ..., autoraise: bool = ...) -> bool: ...
