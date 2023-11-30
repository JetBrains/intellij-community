class JSONDecodeError(ValueError):
    msg: str
    doc: str
    pos: int
    end: int | None
    lineno: int
    colno: int
    endlineno: int | None
    endcolno: int | None
