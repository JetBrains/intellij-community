# Stubs for types
# Note, all classes "defined" here require special handling.

from typing import (
    Any, Callable, Dict, Iterable, Iterator, List, Optional,
    Tuple, Type, TypeVar, Union, overload,
)

_T = TypeVar('_T')

class NoneType: ...
TypeType = type
ObjectType = object

IntType = int
LongType = int  # Really long, but can't reference that due to a mypy import cycle
FloatType = float
BooleanType = bool
ComplexType = complex
StringType = str
UnicodeType = unicode
StringTypes = ...  # type: Tuple[Type[StringType], Type[UnicodeType]]
BufferType = buffer
TupleType = tuple
ListType = list
DictType = dict
DictionaryType = dict

class _Cell:
    cell_contents = ...  # type: Any

class FunctionType:
    func_closure = ...  # type: Optional[Tuple[_Cell, ...]]
    func_code = ...  # type: CodeType
    func_defaults = ...  # type: Optional[Tuple[Any, ...]]
    func_dict = ...  # type: Dict[str, Any]
    func_doc = ...  # type: Optional[str]
    func_globals = ...  # type: Dict[str, Any]
    func_name = ...  # type: str
    __closure__ = func_closure
    __code__ = func_code
    __defaults__ = func_defaults
    __dict__ = func_dict
    __globals__ = func_globals
    __name__ = func_name
    def __call__(self, *args: Any, **kwargs: Any) -> Any: ...
    def __get__(self, obj: Optional[object], type: Optional[type]) -> 'UnboundMethodType': ...

LambdaType = FunctionType

class CodeType:
    co_argcount = ...  # type: int
    co_cellvars = ...  # type: Tuple[str, ...]
    co_code = ...  # type: str
    co_consts = ...  # type: Tuple[Any, ...]
    co_filename = ...  # type: Optional[str]
    co_firstlineno = ...  # type: int
    co_flags = ...  # type: int
    co_freevars = ...  # type: Tuple[str, ...]
    co_lnotab = ...  # type: str
    co_name = ...  # type: str
    co_names = ...  # type: Tuple[str, ...]
    co_nlocals = ...  # type: int
    co_stacksize = ...  # type: int
    co_varnames = ...  # type: Tuple[str, ...]

class GeneratorType:
    gi_code = ...  # type: CodeType
    gi_frame = ...  # type: FrameType
    gi_running = ...  # type: int
    def __iter__(self) -> 'GeneratorType': ...
    def close(self) -> None: ...
    def next(self) -> Any: ...
    def send(self, arg: Any) -> Any: ...
    @overload
    def throw(self, val: BaseException) -> Any: ...
    @overload
    def throw(self, typ: type, val: BaseException = ..., tb: 'TracebackType' = ...) -> Any: ...

class ClassType: ...
class UnboundMethodType:
    im_class = ...  # type: type
    im_func = ...  # type: FunctionType
    im_self = ...  # type: object
    __name__ = ...  # type: str
    __func__ = im_func
    __self__ = im_self
    def __init__(self, func: Callable, obj: object) -> None: ...
    def __call__(self, *args: Any, **kwargs: Any) -> Any: ...

class InstanceType:
    __doc__ = ...  # type: Optional[str]
    __class__ = ...  # type: type
    __module__ = ...  # type: Any

MethodType = UnboundMethodType

class BuiltinFunctionType:
    __self__ = ...  # type: Optional[object]
    def __call__(self, *args: Any, **kwargs: Any) -> Any: ...
BuiltinMethodType = BuiltinFunctionType

class ModuleType:
    __doc__ = ...  # type: Optional[str]
    __file__ = ...  # type: Optional[str]
    __name__ = ...  # type: str
    __package__ = ...  # type: Optional[str]
    __path__ = ...  # type: Optional[Iterable[str]]
    __dict__ = ...  # type: Dict[str, Any]
    def __init__(self, name: str, doc: Optional[str] = ...) -> None: ...
FileType = file
XRangeType = xrange

class TracebackType:
    tb_frame = ...  # type: FrameType
    tb_lasti = ...  # type: int
    tb_lineno = ...  # type: int
    tb_next = ...  # type: TracebackType

class FrameType:
    f_back = ...  # type: FrameType
    f_builtins = ...  # type: Dict[str, Any]
    f_code = ...  # type: CodeType
    f_exc_type = ...  # type: None
    f_exc_value = ...  # type: None
    f_exc_traceback = ...  # type: None
    f_globals = ...  # type: Dict[str, Any]
    f_lasti = ...  # type: int
    f_lineno = ...  # type: int
    f_locals = ...  # type: Dict[str, Any]
    f_restricted = ...  # type: bool
    f_trace = ...  # type: Callable[[], None]

    def clear(self) -> None: ...

SliceType = slice
class EllipsisType: ...

class DictProxyType:
    # TODO is it possible to have non-string keys?
    # no __init__
    def copy(self) -> dict: ...
    def get(self, key: str, default: _T = ...) -> Union[Any, _T]: ...
    def has_key(self, key: str) -> bool: ...
    def items(self) -> List[Tuple[str, Any]]: ...
    def iteritems(self) -> Iterator[Tuple[str, Any]]: ...
    def iterkeys(self) -> Iterator[str]: ...
    def itervalues(self) -> Iterator[Any]: ...
    def keys(self) -> List[str]: ...
    def values(self) -> List[Any]: ...
    def __contains__(self, key: str) -> bool: ...
    def __getitem__(self, key: str) -> Any: ...
    def __iter__(self) -> Iterator[str]: ...
    def __len__(self) -> int: ...

class NotImplementedType: ...

class GetSetDescriptorType:
    __name__ = ...  # type: str
    __objclass__ = ...  # type: type
    def __get__(self, obj: Any, type: type = ...) -> Any: ...
    def __set__(self, obj: Any) -> None: ...
    def __delete__(self, obj: Any) -> None: ...
# Same type on Jython, different on CPython and PyPy, unknown on IronPython.
class MemberDescriptorType:
    __name__ = ...  # type: str
    __objclass__ = ...  # type: type
    def __get__(self, obj: Any, type: type = ...) -> Any: ...
    def __set__(self, obj: Any) -> None: ...
    def __delete__(self, obj: Any) -> None: ...
