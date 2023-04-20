/* Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0 */
/* For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt */

#ifndef _COVERAGE_DATASTACK_H
#define _COVERAGE_DATASTACK_H

#include "util.h"
#include "stats.h"

/* An entry on the data stack.  For each call frame, we need to record all
 * the information needed for CTracer_handle_line to operate as quickly as
 * possible.
 */
typedef struct DataStackEntry {
    /* The current file_data set. Owned. */
    PyObject * file_data;

    /* The disposition object for this frame. A borrowed instance of CFileDisposition. */
    PyObject * disposition;

    /* The FileTracer handling this frame, or None if it's Python.  Borrowed. */
    PyObject * file_tracer;

    /* The line number of the last line recorded, for tracing arcs.
        -1 means there was no previous line, as when entering a code object.
    */
    int last_line;

    BOOL started_context;
} DataStackEntry;

/* A data stack is a dynamically allocated vector of DataStackEntry's. */
typedef struct DataStack {
    int depth;      /* The index of the last-used entry in stack. */
    int alloc;      /* number of entries allocated at stack. */
    /* The file data at each level, or NULL if not recording. */
    DataStackEntry * stack;
} DataStack;


int DataStack_init(Stats * pstats, DataStack *pdata_stack);
void DataStack_dealloc(Stats * pstats, DataStack *pdata_stack);
int DataStack_grow(Stats * pstats, DataStack *pdata_stack);

#endif /* _COVERAGE_DATASTACK_H */
