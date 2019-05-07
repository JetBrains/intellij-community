import os

import _shaded_thriftpy

# dynamically import console.thrift classes into `console_thrift` module
_console_thrift = _shaded_thriftpy.load(os.path.join(os.path.dirname(os.path.realpath(__file__)), "console.thrift"),
                                        module_name="console_thrift")

CompletionOption = _console_thrift.CompletionOption
CompletionOptionType = _console_thrift.CompletionOptionType
DebugValue = _console_thrift.DebugValue
GetArrayResponse = _console_thrift.GetArrayResponse
ArrayData = _console_thrift.ArrayData
ArrayHeaders = _console_thrift.ArrayHeaders
ColHeader = _console_thrift.ColHeader
RowHeader = _console_thrift.RowHeader

UnsupportedArrayTypeException = _console_thrift.UnsupportedArrayTypeException
ExceedingArrayDimensionsException = _console_thrift.ExceedingArrayDimensionsException
KeyboardInterruptException = _console_thrift.KeyboardInterruptException

PythonConsoleFrontendService = _console_thrift.PythonConsoleFrontendService
PythonConsoleBackendService = _console_thrift.PythonConsoleBackendService
