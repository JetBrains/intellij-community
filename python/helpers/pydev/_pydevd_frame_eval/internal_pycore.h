#if PY_VERSION_HEX >= 0x03080000 && PY_VERSION_HEX < 0x03090000
  #define Py_BUILD_CORE // for access to PyInterpreterState
  #include "internal/pycore_pystate.h"
  #undef Py_BUILD_CORE
#elif PY_VERSION_HEX >= 0x03090000
  #define Py_BUILD_CORE // for access to PyInterpreterState
  #include "internal/pycore_interp.h"
  #undef Py_BUILD_CORE
#endif