from typing import Any, Tuple, Union


# The following should pass without error (see #6661):
class Diagnostic:
    def __reduce__(self) -> Union[str, Tuple[Any, ...]]:
        res = super().__reduce__()
        if isinstance(res, tuple) and len(res) >= 3:
            res[2]["_info"] = 42

        return res
