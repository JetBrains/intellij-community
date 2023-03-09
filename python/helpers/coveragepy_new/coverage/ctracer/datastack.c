/* Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0 */
/* For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt */

#include "util.h"
#include "datastack.h"

#define STACK_DELTA    20

int
DataStack_init(Stats *pstats, DataStack *pdata_stack)
{
    pdata_stack->depth = -1;
    pdata_stack->stack = NULL;
    pdata_stack->alloc = 0;
    return RET_OK;
}

void
DataStack_dealloc(Stats *pstats, DataStack *pdata_stack)
{
    int i;

    for (i = 0; i < pdata_stack->alloc; i++) {
        Py_XDECREF(pdata_stack->stack[i].file_data);
    }
    PyMem_Free(pdata_stack->stack);
}

int
DataStack_grow(Stats *pstats, DataStack *pdata_stack)
{
    pdata_stack->depth++;
    if (pdata_stack->depth >= pdata_stack->alloc) {
        /* We've outgrown our data_stack array: make it bigger. */
        int bigger = pdata_stack->alloc + STACK_DELTA;
        DataStackEntry * bigger_data_stack = PyMem_Realloc(pdata_stack->stack, bigger * sizeof(DataStackEntry));
        STATS( pstats->stack_reallocs++; )
        if (bigger_data_stack == NULL) {
            PyErr_NoMemory();
            pdata_stack->depth--;
            return RET_ERROR;
        }
        /* Zero the new entries. */
        memset(bigger_data_stack + pdata_stack->alloc, 0, STACK_DELTA * sizeof(DataStackEntry));

        pdata_stack->stack = bigger_data_stack;
        pdata_stack->alloc = bigger;
    }
    return RET_OK;
}
