from typing import Any

from openpyxl.descriptors.serialisable import Serialisable

class EmbeddedWAVAudioFile(Serialisable):  # type: ignore[misc]
    name: Any
    def __init__(self, name: Any | None = ...) -> None: ...

class Hyperlink(Serialisable):
    tagname: str
    namespace: Any
    invalidUrl: Any
    action: Any
    tgtFrame: Any
    tooltip: Any
    history: Any
    highlightClick: Any
    endSnd: Any
    snd: Any
    extLst: Any
    id: Any
    __elements__: Any
    def __init__(
        self,
        invalidUrl: Any | None = ...,
        action: Any | None = ...,
        tgtFrame: Any | None = ...,
        tooltip: Any | None = ...,
        history: Any | None = ...,
        highlightClick: Any | None = ...,
        endSnd: Any | None = ...,
        snd: Any | None = ...,
        extLst: Any | None = ...,
        id: Any | None = ...,
    ) -> None: ...

class Font(Serialisable):
    tagname: str
    namespace: Any
    typeface: Any
    panose: Any
    pitchFamily: Any
    charset: Any
    def __init__(
        self, typeface: Any | None = ..., panose: Any | None = ..., pitchFamily: Any | None = ..., charset: Any | None = ...
    ) -> None: ...

class CharacterProperties(Serialisable):
    tagname: str
    namespace: Any
    kumimoji: Any
    lang: Any
    altLang: Any
    sz: Any
    b: Any
    i: Any
    u: Any
    strike: Any
    kern: Any
    cap: Any
    spc: Any
    normalizeH: Any
    baseline: Any
    noProof: Any
    dirty: Any
    err: Any
    smtClean: Any
    smtId: Any
    bmk: Any
    ln: Any
    highlight: Any
    latin: Any
    ea: Any
    cs: Any
    sym: Any
    hlinkClick: Any
    hlinkMouseOver: Any
    rtl: Any
    extLst: Any
    noFill: Any
    solidFill: Any
    gradFill: Any
    blipFill: Any
    pattFill: Any
    grpFill: Any
    effectLst: Any
    effectDag: Any
    uLnTx: Any
    uLn: Any
    uFillTx: Any
    uFill: Any
    __elements__: Any
    def __init__(
        self,
        kumimoji: Any | None = ...,
        lang: Any | None = ...,
        altLang: Any | None = ...,
        sz: Any | None = ...,
        b: Any | None = ...,
        i: Any | None = ...,
        u: Any | None = ...,
        strike: Any | None = ...,
        kern: Any | None = ...,
        cap: Any | None = ...,
        spc: Any | None = ...,
        normalizeH: Any | None = ...,
        baseline: Any | None = ...,
        noProof: Any | None = ...,
        dirty: Any | None = ...,
        err: Any | None = ...,
        smtClean: Any | None = ...,
        smtId: Any | None = ...,
        bmk: Any | None = ...,
        ln: Any | None = ...,
        highlight: Any | None = ...,
        latin: Any | None = ...,
        ea: Any | None = ...,
        cs: Any | None = ...,
        sym: Any | None = ...,
        hlinkClick: Any | None = ...,
        hlinkMouseOver: Any | None = ...,
        rtl: Any | None = ...,
        extLst: Any | None = ...,
        noFill: Any | None = ...,
        solidFill: Any | None = ...,
        gradFill: Any | None = ...,
        blipFill: Any | None = ...,
        pattFill: Any | None = ...,
        grpFill: Any | None = ...,
        effectLst: Any | None = ...,
        effectDag: Any | None = ...,
        uLnTx: Any | None = ...,
        uLn: Any | None = ...,
        uFillTx: Any | None = ...,
        uFill: Any | None = ...,
    ) -> None: ...

class TabStop(Serialisable):  # type: ignore[misc]
    pos: Any
    algn: Any
    def __init__(self, pos: Any | None = ..., algn: Any | None = ...) -> None: ...

class TabStopList(Serialisable):  # type: ignore[misc]
    tab: Any
    def __init__(self, tab: Any | None = ...) -> None: ...

class Spacing(Serialisable):
    spcPct: Any
    spcPts: Any
    __elements__: Any
    def __init__(self, spcPct: Any | None = ..., spcPts: Any | None = ...) -> None: ...

class AutonumberBullet(Serialisable):
    type: Any
    startAt: Any
    def __init__(self, type: Any | None = ..., startAt: Any | None = ...) -> None: ...

class ParagraphProperties(Serialisable):
    tagname: str
    namespace: Any
    marL: Any
    marR: Any
    lvl: Any
    indent: Any
    algn: Any
    defTabSz: Any
    rtl: Any
    eaLnBrk: Any
    fontAlgn: Any
    latinLnBrk: Any
    hangingPunct: Any
    lnSpc: Any
    spcBef: Any
    spcAft: Any
    tabLst: Any
    defRPr: Any
    extLst: Any
    buClrTx: Any
    buClr: Any
    buSzTx: Any
    buSzPct: Any
    buSzPts: Any
    buFontTx: Any
    buFont: Any
    buNone: Any
    buAutoNum: Any
    buChar: Any
    buBlip: Any
    __elements__: Any
    def __init__(
        self,
        marL: Any | None = ...,
        marR: Any | None = ...,
        lvl: Any | None = ...,
        indent: Any | None = ...,
        algn: Any | None = ...,
        defTabSz: Any | None = ...,
        rtl: Any | None = ...,
        eaLnBrk: Any | None = ...,
        fontAlgn: Any | None = ...,
        latinLnBrk: Any | None = ...,
        hangingPunct: Any | None = ...,
        lnSpc: Any | None = ...,
        spcBef: Any | None = ...,
        spcAft: Any | None = ...,
        tabLst: Any | None = ...,
        defRPr: Any | None = ...,
        extLst: Any | None = ...,
        buClrTx: Any | None = ...,
        buClr: Any | None = ...,
        buSzTx: Any | None = ...,
        buSzPct: Any | None = ...,
        buSzPts: Any | None = ...,
        buFontTx: Any | None = ...,
        buFont: Any | None = ...,
        buNone: Any | None = ...,
        buAutoNum: Any | None = ...,
        buChar: Any | None = ...,
        buBlip: Any | None = ...,
    ) -> None: ...

class ListStyle(Serialisable):
    tagname: str
    namespace: Any
    defPPr: Any
    lvl1pPr: Any
    lvl2pPr: Any
    lvl3pPr: Any
    lvl4pPr: Any
    lvl5pPr: Any
    lvl6pPr: Any
    lvl7pPr: Any
    lvl8pPr: Any
    lvl9pPr: Any
    extLst: Any
    __elements__: Any
    def __init__(
        self,
        defPPr: Any | None = ...,
        lvl1pPr: Any | None = ...,
        lvl2pPr: Any | None = ...,
        lvl3pPr: Any | None = ...,
        lvl4pPr: Any | None = ...,
        lvl5pPr: Any | None = ...,
        lvl6pPr: Any | None = ...,
        lvl7pPr: Any | None = ...,
        lvl8pPr: Any | None = ...,
        lvl9pPr: Any | None = ...,
        extLst: Any | None = ...,
    ) -> None: ...

class RegularTextRun(Serialisable):
    tagname: str
    namespace: Any
    rPr: Any
    properties: Any
    t: Any
    value: Any
    __elements__: Any
    def __init__(self, rPr: Any | None = ..., t: str = ...) -> None: ...

class LineBreak(Serialisable):
    tagname: str
    namespace: Any
    rPr: Any
    __elements__: Any
    def __init__(self, rPr: Any | None = ...) -> None: ...

class TextField(Serialisable):
    id: Any
    type: Any
    rPr: Any
    pPr: Any
    t: Any
    __elements__: Any
    def __init__(
        self, id: Any | None = ..., type: Any | None = ..., rPr: Any | None = ..., pPr: Any | None = ..., t: Any | None = ...
    ) -> None: ...

class Paragraph(Serialisable):
    tagname: str
    namespace: Any
    pPr: Any
    properties: Any
    endParaRPr: Any
    r: Any
    text: Any
    br: Any
    fld: Any
    __elements__: Any
    def __init__(
        self,
        pPr: Any | None = ...,
        endParaRPr: Any | None = ...,
        r: Any | None = ...,
        br: Any | None = ...,
        fld: Any | None = ...,
    ) -> None: ...

class GeomGuide(Serialisable):
    name: Any
    fmla: Any
    def __init__(self, name: Any | None = ..., fmla: Any | None = ...) -> None: ...

class GeomGuideList(Serialisable):
    gd: Any
    def __init__(self, gd: Any | None = ...) -> None: ...

class PresetTextShape(Serialisable):
    prst: Any
    avLst: Any
    def __init__(self, prst: Any | None = ..., avLst: Any | None = ...) -> None: ...

class TextNormalAutofit(Serialisable):
    fontScale: Any
    lnSpcReduction: Any
    def __init__(self, fontScale: Any | None = ..., lnSpcReduction: Any | None = ...) -> None: ...

class RichTextProperties(Serialisable):
    tagname: str
    namespace: Any
    rot: Any
    spcFirstLastPara: Any
    vertOverflow: Any
    horzOverflow: Any
    vert: Any
    wrap: Any
    lIns: Any
    tIns: Any
    rIns: Any
    bIns: Any
    numCol: Any
    spcCol: Any
    rtlCol: Any
    fromWordArt: Any
    anchor: Any
    anchorCtr: Any
    forceAA: Any
    upright: Any
    compatLnSpc: Any
    prstTxWarp: Any
    scene3d: Any
    extLst: Any
    noAutofit: Any
    normAutofit: Any
    spAutoFit: Any
    flatTx: Any
    __elements__: Any
    def __init__(
        self,
        rot: Any | None = ...,
        spcFirstLastPara: Any | None = ...,
        vertOverflow: Any | None = ...,
        horzOverflow: Any | None = ...,
        vert: Any | None = ...,
        wrap: Any | None = ...,
        lIns: Any | None = ...,
        tIns: Any | None = ...,
        rIns: Any | None = ...,
        bIns: Any | None = ...,
        numCol: Any | None = ...,
        spcCol: Any | None = ...,
        rtlCol: Any | None = ...,
        fromWordArt: Any | None = ...,
        anchor: Any | None = ...,
        anchorCtr: Any | None = ...,
        forceAA: Any | None = ...,
        upright: Any | None = ...,
        compatLnSpc: Any | None = ...,
        prstTxWarp: Any | None = ...,
        scene3d: Any | None = ...,
        extLst: Any | None = ...,
        noAutofit: Any | None = ...,
        normAutofit: Any | None = ...,
        spAutoFit: Any | None = ...,
        flatTx: Any | None = ...,
    ) -> None: ...
