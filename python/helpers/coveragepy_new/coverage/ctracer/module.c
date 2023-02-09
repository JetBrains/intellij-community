/* Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0 */
/* For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt */

#include "util.h"
#include "tracer.h"
#include "filedisp.h"

/* Module definition */

#define MODULE_DOC PyDoc_STR("Fast coverage tracer.")

static PyModuleDef
moduledef = {
    PyModuleDef_HEAD_INIT,
    "coverage.tracer",
    MODULE_DOC,
    -1,
    NULL,       /* methods */
    NULL,
    NULL,       /* traverse */
    NULL,       /* clear */
    NULL
};


PyObject *
PyInit_tracer(void)
{
    PyObject * mod = PyModule_Create(&moduledef);
    if (mod == NULL) {
        return NULL;
    }

    if (CTracer_intern_strings() < 0) {
        return NULL;
    }

    /* Initialize CTracer */
    CTracerType.tp_new = PyType_GenericNew;
    if (PyType_Ready(&CTracerType) < 0) {
        Py_DECREF(mod);
        return NULL;
    }

    Py_INCREF(&CTracerType);
    if (PyModule_AddObject(mod, "CTracer", (PyObject *)&CTracerType) < 0) {
        Py_DECREF(mod);
        Py_DECREF(&CTracerType);
        return NULL;
    }

    /* Initialize CFileDisposition */
    CFileDispositionType.tp_new = PyType_GenericNew;
    if (PyType_Ready(&CFileDispositionType) < 0) {
        Py_DECREF(mod);
        Py_DECREF(&CTracerType);
        return NULL;
    }

    Py_INCREF(&CFileDispositionType);
    if (PyModule_AddObject(mod, "CFileDisposition", (PyObject *)&CFileDispositionType) < 0) {
        Py_DECREF(mod);
        Py_DECREF(&CTracerType);
        Py_DECREF(&CFileDispositionType);
        return NULL;
    }

    return mod;
}
