from warnings import warn

from typing_extensions import deprecated

class Spam:
    def __init__(self):
        self._shape = ""

    @property
    def shape(self) -> str:
        return self._shape

    @shape.setter
    @deprecated("Use of deprecated property setter Spam.shape")
    def shape(self, value: str) -> None:
        self._shape = value

s = Spam()
s.<warning descr="Use of deprecated property setter Spam.shape">shape</warning> = "cube"
s.<warning descr="Use of deprecated property setter Spam.shape">shape</warning> += "edge"