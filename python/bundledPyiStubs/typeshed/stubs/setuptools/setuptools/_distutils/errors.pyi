from .compilers.C.errors import (
    CompileError as CompileError,
    LibError as LibError,
    LinkError as LinkError,
    PreprocessError as PreprocessError,
)
from .compilers.errors import Error as _Error, UnknownFileType as _UnknownFileType

CCompilerError = _Error
UnknownFileError = _UnknownFileType

__all__ = [
    "CCompilerError",
    "CompileError",
    "DistutilsArgError",
    "DistutilsByteCompileError",
    "DistutilsClassError",
    "DistutilsError",
    "DistutilsExecError",
    "DistutilsFileError",
    "DistutilsGetoptError",
    "DistutilsInternalError",
    "DistutilsModuleError",
    "DistutilsOptionError",
    "DistutilsPlatformError",
    "DistutilsSetupError",
    "DistutilsTemplateError",
    "LibError",
    "LinkError",
    "PreprocessError",
    "UnknownFileError",
]

class DistutilsError(Exception): ...
class DistutilsModuleError(DistutilsError): ...
class DistutilsClassError(DistutilsError): ...
class DistutilsGetoptError(DistutilsError): ...
class DistutilsArgError(DistutilsError): ...
class DistutilsFileError(DistutilsError): ...
class DistutilsOptionError(DistutilsError): ...
class DistutilsSetupError(DistutilsError): ...
class DistutilsPlatformError(DistutilsError): ...
class DistutilsExecError(DistutilsError): ...
class DistutilsInternalError(DistutilsError): ...
class DistutilsTemplateError(DistutilsError): ...
class DistutilsByteCompileError(DistutilsError): ...
