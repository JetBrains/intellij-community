import abc
import types
from typing import Any, Callable, Tuple, Union

def valid_annotations(
    p1: int,
    p2: str,
    p3: bytes,
    p4: bytearray,
    p5: memoryview,
    p6: complex,
    p7: float,
    p8: bool,
    p9: object,
    p10: type,
    p11: types.ModuleType,
    p12: types.FunctionType,
    p13: types.BuiltinFunctionType,
    p14: UserDefinedClass,
    p15: AbstractBaseClass,
    p16: int,
    p17: Union[int, str],
    p18: None,
    p19: list,
    p20: list[int],
    p21: tuple,
    p22: Tuple[int, ...],
    p23: Tuple[int, int, str],
    p24: Callable[..., int],
    p25: Callable[[int, str], None],
    p26: Any,
): ...