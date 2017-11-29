from typing import Any, Dict, Optional, Text, Union
from jinja2.utils import Markup as Markup, escape as escape, missing as missing, concat as concat
from jinja2.exceptions import TemplateRuntimeError as TemplateRuntimeError, TemplateNotFound as TemplateNotFound

from jinja2.environment import Environment

to_string = ...  # type: Any
identity = ...  # type: Any

def markup_join(seq): ...
def unicode_join(seq): ...

class TemplateReference:
    def __init__(self, context) -> None: ...
    def __getitem__(self, name): ...

class Context:
    parent = ...  # type: Union[Context, Dict[str, Any]]
    vars = ...  # type: Dict[str, Any]
    environment = ...  # type: Environment
    eval_ctx = ...  # type: Any
    exported_vars = ...  # type: Any
    name = ...  # type: Text
    blocks = ...  # type: Dict[str, Any]
    def __init__(self, environment: Environment, parent: Union[Context, Dict[str, Any]], name: Text, blocks: Dict[str, Any]) -> None: ...
    def super(self, name, current): ...
    def get(self, key, default: Optional[Any] = ...): ...
    def resolve(self, key): ...
    def get_exported(self): ...
    def get_all(self): ...
    def call(__self, __obj, *args, **kwargs): ...
    def derived(self, locals: Optional[Any] = ...): ...
    keys = ...  # type: Any
    values = ...  # type: Any
    items = ...  # type: Any
    iterkeys = ...  # type: Any
    itervalues = ...  # type: Any
    iteritems = ...  # type: Any
    def __contains__(self, name): ...
    def __getitem__(self, key): ...

class BlockReference:
    name = ...  # type: Any
    def __init__(self, name, context, stack, depth) -> None: ...
    @property
    def super(self): ...
    def __call__(self): ...

class LoopContext:
    index0 = ...  # type: int
    depth0 = ...  # type: Any
    def __init__(self, iterable, recurse: Optional[Any] = ..., depth0: int = ...) -> None: ...
    def cycle(self, *args): ...
    first = ...  # type: Any
    last = ...  # type: Any
    index = ...  # type: Any
    revindex = ...  # type: Any
    revindex0 = ...  # type: Any
    depth = ...  # type: Any
    def __len__(self): ...
    def __iter__(self): ...
    def loop(self, iterable): ...
    __call__ = ...  # type: Any
    @property
    def length(self): ...

class LoopContextIterator:
    context = ...  # type: Any
    def __init__(self, context) -> None: ...
    def __iter__(self): ...
    def __next__(self): ...

class Macro:
    name = ...  # type: Any
    arguments = ...  # type: Any
    defaults = ...  # type: Any
    catch_kwargs = ...  # type: Any
    catch_varargs = ...  # type: Any
    caller = ...  # type: Any
    def __init__(self, environment, func, name, arguments, defaults, catch_kwargs, catch_varargs, caller) -> None: ...
    def __call__(self, *args, **kwargs): ...

class Undefined:
    def __init__(self, hint: Optional[Any] = ..., obj: Any = ..., name: Optional[Any] = ..., exc: Any = ...) -> None: ...
    def __getattr__(self, name): ...
    __add__ = ...  # type: Any
    __radd__ = ...  # type: Any
    __mul__ = ...  # type: Any
    __rmul__ = ...  # type: Any
    __div__ = ...  # type: Any
    __rdiv__ = ...  # type: Any
    __truediv__ = ...  # type: Any
    __rtruediv__ = ...  # type: Any
    __floordiv__ = ...  # type: Any
    __rfloordiv__ = ...  # type: Any
    __mod__ = ...  # type: Any
    __rmod__ = ...  # type: Any
    __pos__ = ...  # type: Any
    __neg__ = ...  # type: Any
    __call__ = ...  # type: Any
    __getitem__ = ...  # type: Any
    __lt__ = ...  # type: Any
    __le__ = ...  # type: Any
    __gt__ = ...  # type: Any
    __ge__ = ...  # type: Any
    __int__ = ...  # type: Any
    __float__ = ...  # type: Any
    __complex__ = ...  # type: Any
    __pow__ = ...  # type: Any
    __rpow__ = ...  # type: Any
    def __eq__(self, other): ...
    def __ne__(self, other): ...
    def __hash__(self): ...
    def __len__(self): ...
    def __iter__(self): ...
    def __nonzero__(self): ...
    __bool__ = ...  # type: Any

def make_logging_undefined(logger: Optional[Any] = ..., base: Optional[Any] = ...): ...

class DebugUndefined(Undefined): ...

class StrictUndefined(Undefined):
    __iter__ = ...  # type: Any
    __len__ = ...  # type: Any
    __nonzero__ = ...  # type: Any
    __eq__ = ...  # type: Any
    __ne__ = ...  # type: Any
    __bool__ = ...  # type: Any
    __hash__ = ...  # type: Any
