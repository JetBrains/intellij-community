#if PY_VERSION_HEX >= 0x03080000
#define Py_BUILD_CORE // for access to PyInterpreterState
#include "internal/pycore_pystate.h"
#undef Py_BUILD_CORE
#endif