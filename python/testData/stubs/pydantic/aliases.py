from typing import Union


class AliasPath:
    path: list[int | str]

    def __init__(self, first_arg: str, *args: str | int) -> None:
        ...


class AliasChoices:
    choices: list[Union[str, AliasPath]]

    def __init__(self, first_choice: Union[str, AliasPath], *choices: Union[str, AliasPath]) -> None:
        ...
