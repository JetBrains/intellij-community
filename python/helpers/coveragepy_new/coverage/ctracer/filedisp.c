/* Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0 */
/* For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt */

#include "util.h"
#include "filedisp.h"

void
CFileDisposition_dealloc(CFileDisposition *self)
{
    Py_XDECREF(self->original_filename);
    Py_XDECREF(self->canonical_filename);
    Py_XDECREF(self->source_filename);
    Py_XDECREF(self->trace);
    Py_XDECREF(self->reason);
    Py_XDECREF(self->file_tracer);
    Py_XDECREF(self->has_dynamic_filename);
}

static PyMemberDef
CFileDisposition_members[] = {
    { "original_filename",      T_OBJECT, offsetof(CFileDisposition, original_filename), 0,
            PyDoc_STR("") },

    { "canonical_filename",     T_OBJECT, offsetof(CFileDisposition, canonical_filename), 0,
            PyDoc_STR("") },

    { "source_filename",        T_OBJECT, offsetof(CFileDisposition, source_filename), 0,
            PyDoc_STR("") },

    { "trace",                  T_OBJECT, offsetof(CFileDisposition, trace), 0,
            PyDoc_STR("") },

    { "reason",                 T_OBJECT, offsetof(CFileDisposition, reason), 0,
            PyDoc_STR("") },

    { "file_tracer",            T_OBJECT, offsetof(CFileDisposition, file_tracer), 0,
            PyDoc_STR("") },

    { "has_dynamic_filename",   T_OBJECT, offsetof(CFileDisposition, has_dynamic_filename), 0,
            PyDoc_STR("") },

    { NULL }
};

PyTypeObject
CFileDispositionType = {
    PyVarObject_HEAD_INIT(NULL, 0)
    "coverage.CFileDispositionType",        /*tp_name*/
    sizeof(CFileDisposition),  /*tp_basicsize*/
    0,                         /*tp_itemsize*/
    (destructor)CFileDisposition_dealloc, /*tp_dealloc*/
    0,                         /*tp_print*/
    0,                         /*tp_getattr*/
    0,                         /*tp_setattr*/
    0,                         /*tp_compare*/
    0,                         /*tp_repr*/
    0,                         /*tp_as_number*/
    0,                         /*tp_as_sequence*/
    0,                         /*tp_as_mapping*/
    0,                         /*tp_hash */
    0,                         /*tp_call*/
    0,                         /*tp_str*/
    0,                         /*tp_getattro*/
    0,                         /*tp_setattro*/
    0,                         /*tp_as_buffer*/
    Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, /*tp_flags*/
    "CFileDisposition objects", /* tp_doc */
    0,                         /* tp_traverse */
    0,                         /* tp_clear */
    0,                         /* tp_richcompare */
    0,                         /* tp_weaklistoffset */
    0,                         /* tp_iter */
    0,                         /* tp_iternext */
    0,                         /* tp_methods */
    CFileDisposition_members,  /* tp_members */
    0,                         /* tp_getset */
    0,                         /* tp_base */
    0,                         /* tp_dict */
    0,                         /* tp_descr_get */
    0,                         /* tp_descr_set */
    0,                         /* tp_dictoffset */
    0,                         /* tp_init */
    0,                         /* tp_alloc */
    0,                         /* tp_new */
};
