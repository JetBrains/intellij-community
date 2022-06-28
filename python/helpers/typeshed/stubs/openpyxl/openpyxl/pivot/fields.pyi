from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class Index(Serialisable):
    tagname: str
    v: Any
    def __init__(self, v: int = ...) -> None: ...

class Tuple(Serialisable):  # type: ignore[misc]
    fld: Any
    hier: Any
    item: Any
    def __init__(self, fld: Any | None = ..., hier: Any | None = ..., item: Any | None = ...) -> None: ...

class TupleList(Serialisable):  # type: ignore[misc]
    c: Any
    tpl: Any
    __elements__: Any
    def __init__(self, c: Any | None = ..., tpl: Any | None = ...) -> None: ...

class Missing(Serialisable):
    tagname: str
    tpls: Any
    x: Any
    u: Any
    f: Any
    c: Any
    cp: Any
    bc: Any
    fc: Any
    i: Any
    un: Any
    st: Any
    b: Any
    __elements__: Any
    def __init__(
        self,
        tpls=...,
        x=...,
        u: Any | None = ...,
        f: Any | None = ...,
        c: Any | None = ...,
        cp: Any | None = ...,
        _in: Any | None = ...,
        bc: Any | None = ...,
        fc: Any | None = ...,
        i: Any | None = ...,
        un: Any | None = ...,
        st: Any | None = ...,
        b: Any | None = ...,
    ) -> None: ...

class Number(Serialisable):
    tagname: str
    tpls: Any
    x: Any
    v: Any
    u: Any
    f: Any
    c: Any
    cp: Any
    bc: Any
    fc: Any
    i: Any
    un: Any
    st: Any
    b: Any
    __elements__: Any
    def __init__(
        self,
        tpls=...,
        x=...,
        v: Any | None = ...,
        u: Any | None = ...,
        f: Any | None = ...,
        c: Any | None = ...,
        cp: Any | None = ...,
        _in: Any | None = ...,
        bc: Any | None = ...,
        fc: Any | None = ...,
        i: Any | None = ...,
        un: Any | None = ...,
        st: Any | None = ...,
        b: Any | None = ...,
    ) -> None: ...

class Error(Serialisable):
    tagname: str
    tpls: Any
    x: Any
    v: Any
    u: Any
    f: Any
    c: Any
    cp: Any
    bc: Any
    fc: Any
    i: Any
    un: Any
    st: Any
    b: Any
    __elements__: Any
    def __init__(
        self,
        tpls: Any | None = ...,
        x=...,
        v: Any | None = ...,
        u: Any | None = ...,
        f: Any | None = ...,
        c: Any | None = ...,
        cp: Any | None = ...,
        _in: Any | None = ...,
        bc: Any | None = ...,
        fc: Any | None = ...,
        i: Any | None = ...,
        un: Any | None = ...,
        st: Any | None = ...,
        b: Any | None = ...,
    ) -> None: ...

class Boolean(Serialisable):
    tagname: str
    x: Any
    v: Any
    u: Any
    f: Any
    c: Any
    cp: Any
    __elements__: Any
    def __init__(
        self, x=..., v: Any | None = ..., u: Any | None = ..., f: Any | None = ..., c: Any | None = ..., cp: Any | None = ...
    ) -> None: ...

class Text(Serialisable):
    tagname: str
    tpls: Any
    x: Any
    v: Any
    u: Any
    f: Any
    c: Any
    cp: Any
    bc: Any
    fc: Any
    i: Any
    un: Any
    st: Any
    b: Any
    __elements__: Any
    def __init__(
        self,
        tpls=...,
        x=...,
        v: Any | None = ...,
        u: Any | None = ...,
        f: Any | None = ...,
        c: Any | None = ...,
        cp: Any | None = ...,
        _in: Any | None = ...,
        bc: Any | None = ...,
        fc: Any | None = ...,
        i: Any | None = ...,
        un: Any | None = ...,
        st: Any | None = ...,
        b: Any | None = ...,
    ) -> None: ...

class DateTimeField(Serialisable):
    tagname: str
    x: Any
    v: Any
    u: Any
    f: Any
    c: Any
    cp: Any
    __elements__: Any
    def __init__(
        self, x=..., v: Any | None = ..., u: Any | None = ..., f: Any | None = ..., c: Any | None = ..., cp: Any | None = ...
    ) -> None: ...
