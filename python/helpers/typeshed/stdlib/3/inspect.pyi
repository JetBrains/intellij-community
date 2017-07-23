# Stubs for inspect

from typing import (AbstractSet, Any, Tuple, List, Dict, Callable, Generator,
                    Mapping, MutableMapping, NamedTuple, Optional, Sequence, Union,
                    )
from types import FrameType, ModuleType, TracebackType

#
# Types and members
#
ModuleInfo = NamedTuple('ModuleInfo', [('name', str),
                                       ('suffix', str),
                                       ('mode', str),
                                       ('module_type', int),
                                       ])
def getmembers(object: object,
               predicate: Callable[[Any], bool] = ...,
               ) -> List[Tuple[str, Any]]: ...
def getmoduleinfo(path: str) -> Optional[ModuleInfo]: ...
def getmodulename(path: str) -> Optional[str]: ...

def ismodule(object: object) -> bool: ...
def isclass(object: object) -> bool: ...
def ismethod(object: object) -> bool: ...
def isfunction(object: object) -> bool: ...
def isgeneratorfunction(object: object) -> bool: ...
def isgenerator(object: object) -> bool: ...

# Python 3.5+
def iscoroutinefunction(object: object) -> bool: ...
def iscoroutine(object: object) -> bool: ...
def isawaitable(object: object) -> bool: ...

def istraceback(object: object) -> bool: ...
def isframe(object: object) -> bool: ...
def iscode(object: object) -> bool: ...
def isbuiltin(object: object) -> bool: ...
def isroutine(object: object) -> bool: ...
def isabstract(object: object) -> bool: ...
def ismethoddescriptor(object: object) -> bool: ...
def isdatadescriptor(object: object) -> bool: ...
def isgetsetdescriptor(object: object) -> bool: ...
def ismemberdescriptor(object: object) -> bool: ...


#
# Retrieving source code
#
def getdoc(object: object) -> str: ...
def getcomments(object: object) -> str: ...
def getfile(object: object) -> str: ...
def getmodule(object: object) -> ModuleType: ...
def getsourcefile(object: object) -> str: ...
# TODO restrict to "module, class, method, function, traceback, frame,
# or code object"
def getsourcelines(object: object) -> Tuple[List[str], int]: ...
# TODO restrict to "a module, class, method, function, traceback, frame,
# or code object"
def getsource(object: object) -> str: ...
def cleandoc(doc: str) -> str: ...


#
# Introspecting callables with the Signature object (Python 3.3+)
#
def signature(callable: Callable[..., Any],
              *,
              follow_wrapped: bool = True) -> 'Signature': ...

class Signature:
    def __init__(self,
                 parameters: Optional[Sequence['Parameter']] = ...,
                 *,
                 return_annotation: Any = ...) -> None: ...
    # TODO: can we be more specific here?
    empty = ...  # type: object

    parameters = ...  # type: Mapping[str, 'Parameter']

    # TODO: can we be more specific here?
    return_annotation = ...  # type: Any

    def bind(self, *args: Any, **kwargs: Any) -> 'BoundArguments': ...
    def bind_partial(self, *args: Any, **kwargs: Any) -> 'BoundArguments': ...
    def replace(self,
                *,
                parameters: Optional[Sequence['Parameter']] = ...,
                return_annotation: Any = ...) -> 'Signature': ...

    # Python 3.5+
    @classmethod
    def from_callable(cls,
                      obj: Callable[..., Any],
                      *,
                      follow_wrapped: bool = True) -> 'Signature': ...

# The name is the same as the enum's name in CPython
class _ParameterKind: ...

class Parameter:
    def __init__(self,
                 name: str,
                 kind: _ParameterKind,
                 *,
                 default: Any = ...,
                 annotation: Any = ...) -> None: ...
    empty = ...  # type: Any
    name = ...  # type: str
    default = ...  # type: Any
    annotation = ...  # type: Any

    kind = ...  # type: _ParameterKind
    POSITIONAL_ONLY = ...  # type: _ParameterKind
    POSITIONAL_OR_KEYWORD = ...  # type: _ParameterKind
    VAR_POSITIONAL = ...  # type: _ParameterKind
    KEYWORD_ONLY = ...  # type: _ParameterKind
    VAR_KEYWORD = ...  # type: _ParameterKind

    def replace(self,
                *,
                name: Optional[str] = ...,
                kind: Optional[_ParameterKind] = ...,
                default: Any = ...,
                annotation: Any = ...) -> 'Parameter': ...

class BoundArguments:
    arguments = ...  # type: MutableMapping[str, Any]
    args = ...  # Tuple[Any, ...]
    kwargs = ...  # Dict[str, Any]
    signature = ...  # type: Signature

    # Python 3.5+
    def apply_defaults(self) -> None: ...


#
# Classes and functions
#

# TODO: The actual return type should be List[_ClassTreeItem] but mypy doesn't
# seem to be supporting this at the moment:
# _ClassTreeItem = Union[List['_ClassTreeItem'], Tuple[type, Tuple[type, ...]]]
def getclasstree(classes: List[type], unique: bool = ...) -> Any: ...

ArgSpec = NamedTuple('ArgSpec', [('args', List[str]),
                                 ('varargs', str),
                                 ('keywords', str),
                                 ('defaults', tuple),
                                 ])

def getargspec(func: object) -> ArgSpec: ...

FullArgSpec = NamedTuple('FullArgSpec', [('args', List[str]),
                                         ('varargs', str),
                                         ('varkw', str),
                                         ('defaults', tuple),
                                         ('kwonlyargs', List[str]),
                                         ('kwonlydefaults', Dict[str, Any]),
                                         ('annotations', Dict[str, Any]),
                                         ])

def getfullargspec(func: object) -> FullArgSpec: ...

# TODO make the field types more specific here
ArgInfo = NamedTuple('ArgInfo', [('args', List[str]),
                                 ('varargs', Optional[str]),
                                 ('keywords', Optional[str]),
                                 ('locals', Dict[str, Any]),
                                 ])

def getargvalues(frame: FrameType) -> ArgInfo: ...
def formatargspec(args: List[str],
                  varargs: Optional[str] = ...,
                  varkw: Optional[str] = ...,
                  defaults: Optional[Tuple[Any, ...]] = ...,
                  kwonlyargs: Optional[List[str]] = ...,
                  kwonlydefaults: Optional[Dict[str, Any]] = ...,
                  annotations: Optional[Dict[str, Any]] = ...,
                  formatarg: Optional[Callable[[str], str]] = ...,
                  formatvarargs: Optional[Callable[[str], str]] = ...,
                  formatvarkw: Optional[Callable[[str], str]] = ...,
                  formatvalue: Optional[Callable[[Any], str]] = ...,
                  formatreturns: Optional[Callable[[Any], str]] = ...,
                  formatannotations: Optional[Callable[[Any], str]] = ...,
                  ) -> str: ...
def formatargvalues(args: List[str],
                    varargs: Optional[str] = ...,
                    varkw: Optional[str] = ...,
                    locals: Optional[Dict[str, Any]] = ...,
                    formatarg: Optional[Callable[[str], str]] = ...,
                    formatvarargs: Optional[Callable[[str], str]] = ...,
                    formatvarkw: Optional[Callable[[str], str]] = ...,
                    formatvalue: Optional[Callable[[Any], str]] = ...,
                    ) -> str: ...
def getmro(cls: type) -> Tuple[type, ...]: ...

# Python 3.2+
def getcallargs(func: Callable[..., Any],
                *args: Any,
                **kwds: Any) -> Dict[str, Any]: ...


# Python 3.3+
ClosureVars = NamedTuple('ClosureVars', [('nonlocals', Mapping[str, Any]),
                                         ('globals', Mapping[str, Any]),
                                         ('builtins', Mapping[str, Any]),
                                         ('unbound', AbstractSet[str]),
                                         ])
def getclosurevars(func: Callable[..., Any]) -> ClosureVars: ...

# Python 3.4+
def unwrap(func: Callable[..., Any],
           *,
           stop: Callable[[Any], Any]) -> Any: ...


#
# The interpreter stack
#

# Python 3.5+ (functions returning it used to return regular tuples)
FrameInfo = NamedTuple('FrameInfo', [('frame', FrameType),
                                     ('filename', str),
                                     ('lineno', int),
                                     ('function', str),
                                     ('code_context', List[str]),
                                     ('index', int),
                                     ])

# TODO make the frame type more specific
def getframeinfo(frame: Any, context: int = 1) -> FrameInfo: ...
def getouterframes(frame: Any, context: int = 1) -> List[FrameInfo]: ...
def getinnerframes(traceback: TracebackType, context: int = 1) -> List[FrameInfo]:
    ...
def currentframe() -> Optional[FrameType]: ...
def stack(context: int = 1) -> List[FrameInfo]: ...
def trace(context: int = 1) -> List[FrameInfo]: ...

#
# Fetching attributes statically
#

# Python 3.2+
def getattr_static(obj: object, attr: str, default: Optional[Any] = ...) -> Any: ...


#
# Current State of Generators and Coroutines
#

# TODO In the next two blocks of code, can we be more specific regarding the
# type of the "enums"?

# Python 3.2+
GEN_CREATED = ...  # type: str
GEN_RUNNING = ...  # type: str
GEN_SUSPENDED = ...  # type: str
GEN_CLOSED = ...  # type: str
def getgeneratorstate(generator: Generator[Any, Any, Any]) -> str: ...

# Python 3.5+
CORO_CREATED = ...  # type: str
CORO_RUNNING = ...  # type: str
CORO_SUSPENDED = ...  # type: str
CORO_CLOSED = ...  # type: str
# TODO can we be more specific than "object"?
def getcoroutinestate(coroutine: object) -> str: ...

# Python 3.3+
def getgeneratorlocals(generator: Generator[Any, Any, Any]) -> Dict[str, Any]: ...

# Python 3.5+
# TODO can we be more specific than "object"?
def getcoroutinelocals(coroutine: object) -> Dict[str, Any]: ...


#
# The following seems undocumented but it was already present in this file
#
_object = object

# namedtuple('Attribute', 'name kind defining_class object')
class Attribute(tuple):
    name = ...  # type: str
    kind = ...  # type: str
    defining_class = ...  # type: type
    object = ...  # type: _object

def classify_class_attrs(cls: type) -> List[Attribute]: ...
