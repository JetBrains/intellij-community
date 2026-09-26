/* Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0 */
/* For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt */

#ifndef _COVERAGE_TRACER_H
#define _COVERAGE_TRACER_H

#include "util.h"
#include "structmember.h"
#include "frameobject.h"
#include "opcode.h"

#include "datastack.h"

/* The CTracer type. */

typedef struct CTracer {
    PyObject_HEAD

    /* Python objects manipulated directly by the Collector class. */
    PyObject * should_trace;
    PyObject * check_include;
    PyObject * warn;
    PyObject * concur_id_func;
    PyObject * data;
    PyObject * file_tracers;
    PyObject * should_trace_cache;
    PyObject * trace_arcs;
    PyObject * should_start_context;
    PyObject * switch_context;
    PyObject * disable_plugin;

    /* Has the tracer been started? */
    BOOL started;
    /* Are we tracing arcs, or just lines? */
    BOOL tracing_arcs;
    /* Have we had any activity? */
    BOOL activity;
    /* The current dynamic context. */
    PyObject * context;

    /*
        The data stack is a stack of sets.  Each set collects
        data for a single source file.  The data stack parallels the call stack:
        each call pushes the new frame's file data onto the data stack, and each
        return pops file data off.

        The file data is a set whose form depends on the tracing options.
        If tracing arcs, the values are line number pairs.  If not tracing arcs,
        the values are line numbers.
    */

    DataStack data_stack;           /* Used if we aren't doing concurrency. */

    PyObject * data_stack_index;    /* Used if we are doing concurrency. */
    DataStack * data_stacks;
    int data_stacks_alloc;
    int data_stacks_used;
    DataStack * pdata_stack;

    /* The current file's data stack entry. */
    DataStackEntry * pcur_entry;

    Stats stats;
} CTracer;

int CTracer_intern_strings(void);

extern PyTypeObject CTracerType;

#endif /* _COVERAGE_TRACER_H */
