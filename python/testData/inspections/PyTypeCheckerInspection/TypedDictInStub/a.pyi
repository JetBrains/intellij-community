from typing import TypedDict


class Cinema(TypedDict, total=False):
    name: str
    id: int


avrora: Cinema
rodina: Cinema
luksor: Cinema