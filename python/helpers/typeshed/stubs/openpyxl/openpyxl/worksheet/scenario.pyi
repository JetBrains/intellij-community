from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class InputCells(Serialisable):
    tagname: str
    r: Any
    deleted: Any
    undone: Any
    val: Any
    numFmtId: Any
    def __init__(
        self, r: Any | None = ..., deleted: bool = ..., undone: bool = ..., val: Any | None = ..., numFmtId: Any | None = ...
    ) -> None: ...

class Scenario(Serialisable):
    tagname: str
    inputCells: Any
    name: Any
    locked: Any
    hidden: Any
    user: Any
    comment: Any
    __elements__: Any
    __attrs__: Any
    def __init__(
        self,
        inputCells=...,
        name: Any | None = ...,
        locked: bool = ...,
        hidden: bool = ...,
        count: Any | None = ...,
        user: Any | None = ...,
        comment: Any | None = ...,
    ) -> None: ...
    @property
    def count(self): ...

class ScenarioList(Serialisable):
    tagname: str
    scenario: Any
    current: Any
    show: Any
    sqref: Any
    __elements__: Any
    def __init__(self, scenario=..., current: Any | None = ..., show: Any | None = ..., sqref: Any | None = ...) -> None: ...
    def append(self, scenario) -> None: ...
    def __bool__(self): ...
