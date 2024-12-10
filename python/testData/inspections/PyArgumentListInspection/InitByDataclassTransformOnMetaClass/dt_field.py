import dataclasses


class DataclassField:
    def __init__(self,
                 *,
                 kw_only=dataclasses.MISSING,
                 default=dataclasses.MISSING,
                 default_factory=dataclasses.MISSING):
        ...
