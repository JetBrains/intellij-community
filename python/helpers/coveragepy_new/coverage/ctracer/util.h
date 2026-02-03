/* Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0 */
/* For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt */

#ifndef _COVERAGE_UTIL_H
#define _COVERAGE_UTIL_H

#include <Python.h>

/* Compile-time debugging helpers */
#undef WHAT_LOG         /* Define to log the WHAT params in the trace function. */
#undef TRACE_LOG        /* Define to log our bookkeeping. */
#undef COLLECT_STATS    /* Collect counters: stats are printed when tracer is stopped. */
#undef DO_NOTHING       /* Define this to make the tracer do nothing. */

#if PY_VERSION_HEX >= 0x030B00A0
// 3.11 moved f_lasti into an internal structure. This is totally the wrong way
// to make this work, but it's all I've got until https://bugs.python.org/issue40421
// is resolved.
#include <internal/pycore_frame.h>
#if PY_VERSION_HEX >= 0x030B00A7
#define MyFrame_GetLasti(f)     (PyFrame_GetLasti(f))
#else
#define MyFrame_GetLasti(f)     ((f)->f_frame->f_lasti * 2)
#endif
#elif PY_VERSION_HEX >= 0x030A00A7
// The f_lasti field changed meaning in 3.10.0a7. It had been bytes, but
// now is instructions, so we need to adjust it to use it as a byte index.
#define MyFrame_GetLasti(f)     ((f)->f_lasti * 2)
#else
#define MyFrame_GetLasti(f)     ((f)->f_lasti)
#endif

// Access f_code should be done through a helper starting in 3.9.
#if PY_VERSION_HEX >= 0x03090000
#define MyFrame_GetCode(f)      (PyFrame_GetCode(f))
#else
#define MyFrame_GetCode(f)      ((f)->f_code)
#endif

#if PY_VERSION_HEX >= 0x030B00B1
#define MyCode_GetCode(co)      (PyCode_GetCode(co))
#define MyCode_FreeCode(code)   Py_XDECREF(code)
#elif PY_VERSION_HEX >= 0x030B00A7
#define MyCode_GetCode(co)      (PyObject_GetAttrString((PyObject *)(co), "co_code"))
#define MyCode_FreeCode(code)   Py_XDECREF(code)
#else
#define MyCode_GetCode(co)      ((co)->co_code)
#define MyCode_FreeCode(code)
#endif

/* The values returned to indicate ok or error. */
#define RET_OK      0
#define RET_ERROR   -1

/* Nicer booleans */
typedef int BOOL;
#define FALSE   0
#define TRUE    1

#if SIZEOF_LONG_LONG < 8
#error long long too small!
#endif
typedef unsigned long long uint64;

/* Only for extreme machete-mode debugging! */
#define CRASH       { printf("*** CRASH! ***\n"); *((int*)1) = 1; }

#endif /* _COVERAGE_UTIL_H */
