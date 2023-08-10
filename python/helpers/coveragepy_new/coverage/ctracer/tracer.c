/* Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0 */
/* For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt */

/* C-based Tracer for coverage.py. */

#include "util.h"
#include "datastack.h"
#include "filedisp.h"
#include "tracer.h"

/* Python C API helpers. */

static int
pyint_as_int(PyObject * pyint, int *pint)
{
    int the_int = (int)PyLong_AsLong(pyint);
    if (the_int == -1 && PyErr_Occurred()) {
        return RET_ERROR;
    }

    *pint = the_int;
    return RET_OK;
}


/* Interned strings to speed GetAttr etc. */

static PyObject *str_trace;
static PyObject *str_file_tracer;
static PyObject *str__coverage_enabled;
static PyObject *str__coverage_plugin;
static PyObject *str__coverage_plugin_name;
static PyObject *str_dynamic_source_filename;
static PyObject *str_line_number_range;

int
CTracer_intern_strings(void)
{
    int ret = RET_ERROR;

#define INTERN_STRING(v, s)                     \
    v = PyUnicode_InternFromString(s);          \
    if (v == NULL) {                            \
        goto error;                             \
    }

    INTERN_STRING(str_trace, "trace")
    INTERN_STRING(str_file_tracer, "file_tracer")
    INTERN_STRING(str__coverage_enabled, "_coverage_enabled")
    INTERN_STRING(str__coverage_plugin, "_coverage_plugin")
    INTERN_STRING(str__coverage_plugin_name, "_coverage_plugin_name")
    INTERN_STRING(str_dynamic_source_filename, "dynamic_source_filename")
    INTERN_STRING(str_line_number_range, "line_number_range")

    ret = RET_OK;

error:
    return ret;
}

static void CTracer_disable_plugin(CTracer *self, PyObject * disposition);

static int
CTracer_init(CTracer *self, PyObject *args_unused, PyObject *kwds_unused)
{
    int ret = RET_ERROR;

    if (DataStack_init(&self->stats, &self->data_stack) < 0) {
        goto error;
    }

    self->pdata_stack = &self->data_stack;

    self->context = Py_None;
    Py_INCREF(self->context);

    ret = RET_OK;
    goto ok;

error:
    STATS( self->stats.errors++; )

ok:
    return ret;
}

static void
CTracer_dealloc(CTracer *self)
{
    int i;

    if (self->started) {
        PyEval_SetTrace(NULL, NULL);
    }

    Py_XDECREF(self->should_trace);
    Py_XDECREF(self->check_include);
    Py_XDECREF(self->warn);
    Py_XDECREF(self->concur_id_func);
    Py_XDECREF(self->data);
    Py_XDECREF(self->file_tracers);
    Py_XDECREF(self->should_trace_cache);
    Py_XDECREF(self->should_start_context);
    Py_XDECREF(self->switch_context);
    Py_XDECREF(self->context);
    Py_XDECREF(self->disable_plugin);

    DataStack_dealloc(&self->stats, &self->data_stack);
    if (self->data_stacks) {
        for (i = 0; i < self->data_stacks_used; i++) {
            DataStack_dealloc(&self->stats, self->data_stacks + i);
        }
        PyMem_Free(self->data_stacks);
    }

    Py_XDECREF(self->data_stack_index);

    Py_TYPE(self)->tp_free((PyObject*)self);
}

#if TRACE_LOG
/* Set debugging constants: a file substring and line number to start logging. */
static const char * start_file = "badasync.py";
static int start_line = 1;

static const char *
indent(int n)
{
    static const char * spaces =
        "                                                                    "
        "                                                                    "
        "                                                                    "
        "                                                                    "
        ;
    return spaces + strlen(spaces) - n*2;
}

static BOOL logging = FALSE;

static void
CTracer_showlog(CTracer * self, int lineno, PyObject * filename, const char * msg)
{
    if (logging) {
        int depth = self->pdata_stack->depth;
        printf("%x: %s%3d ", (int)self, indent(depth), depth);
        if (lineno) {
            printf("%4d", lineno);
        }
        else {
            printf("    ");
        }
        if (filename) {
            PyObject *ascii = PyUnicode_AsASCIIString(filename);
            printf(" %s", PyBytes_AS_STRING(ascii));
            Py_DECREF(ascii);
        }
        if (msg) {
            printf(" %s", msg);
        }
        printf("\n");
    }
}

#define SHOWLOG(l,f,m)    CTracer_showlog(self,l,f,m)
#else
#define SHOWLOG(l,f,m)
#endif /* TRACE_LOG */

#if WHAT_LOG
static const char * what_sym[] = {"CALL", "EXC ", "LINE", "RET "};
#endif

/* Record a pair of integers in self->pcur_entry->file_data. */
static int
CTracer_record_pair(CTracer *self, int l1, int l2)
{
    int ret = RET_ERROR;
    PyObject * packed_obj = NULL;
    uint64 packed = 0;

    // Conceptually, data is a set of tuples (l1, l2), but that literally
    // making a set of tuples would require us to construct a tuple just to
    // see if we'd already recorded an arc.  On many-times-executed code,
    // that would mean we construct a tuple, find the tuple is already in the
    // set, then discard the tuple.  We can avoid that overhead by packing
    // the two line numbers into one integer instead.
    // See collector.py:flush_data for the Python code that unpacks this.
    if (l1 < 0) {
        packed |= (1LL << 40);
        l1 = -l1;
    }
    if (l2 < 0) {
        packed |= (1LL << 41);
        l2 = -l2;
    }
    packed |= (((uint64)l2) << 20) + (uint64)l1;
    packed_obj = PyLong_FromUnsignedLongLong(packed);
    if (packed_obj == NULL) {
        goto error;
    }

    if (PySet_Add(self->pcur_entry->file_data, packed_obj) < 0) {
        goto error;
    }

    ret = RET_OK;

error:
    Py_XDECREF(packed_obj);

    return ret;
}

/* Set self->pdata_stack to the proper data_stack to use. */
static int
CTracer_set_pdata_stack(CTracer *self)
{
    int ret = RET_ERROR;
    PyObject * co_obj = NULL;
    PyObject * stack_index = NULL;

    if (self->concur_id_func != Py_None) {
        int the_index = 0;

        if (self->data_stack_index == NULL) {
            PyObject * weakref = NULL;

            weakref = PyImport_ImportModule("weakref");
            if (weakref == NULL) {
                goto error;
            }
            STATS( self->stats.pycalls++; )
            self->data_stack_index = PyObject_CallMethod(weakref, "WeakKeyDictionary", NULL);
            Py_XDECREF(weakref);

            if (self->data_stack_index == NULL) {
                goto error;
            }
        }

        STATS( self->stats.pycalls++; )
        co_obj = PyObject_CallObject(self->concur_id_func, NULL);
        if (co_obj == NULL) {
            goto error;
        }
        stack_index = PyObject_GetItem(self->data_stack_index, co_obj);
        if (stack_index == NULL) {
            /* PyObject_GetItem sets an exception if it didn't find the thing. */
            PyErr_Clear();

            /* A new concurrency object.  Make a new data stack. */
            the_index = self->data_stacks_used;
            stack_index = PyLong_FromLong((long)the_index);
            if (stack_index == NULL) {
                goto error;
            }
            if (PyObject_SetItem(self->data_stack_index, co_obj, stack_index) < 0) {
                goto error;
            }
            self->data_stacks_used++;
            if (self->data_stacks_used >= self->data_stacks_alloc) {
                int bigger = self->data_stacks_alloc + 10;
                DataStack * bigger_stacks = PyMem_Realloc(self->data_stacks, bigger * sizeof(DataStack));
                if (bigger_stacks == NULL) {
                    PyErr_NoMemory();
                    goto error;
                }
                self->data_stacks = bigger_stacks;
                self->data_stacks_alloc = bigger;
            }
            DataStack_init(&self->stats, &self->data_stacks[the_index]);
        }
        else {
            if (pyint_as_int(stack_index, &the_index) < 0) {
                goto error;
            }
        }

        self->pdata_stack = &self->data_stacks[the_index];
    }
    else {
        self->pdata_stack = &self->data_stack;
    }

    ret = RET_OK;

error:

    Py_XDECREF(co_obj);
    Py_XDECREF(stack_index);

    return ret;
}

/*
 * Parts of the trace function.
 */

static int
CTracer_handle_call(CTracer *self, PyFrameObject *frame)
{
    int ret = RET_ERROR;
    int ret2;

    /* Owned references that we clean up at the very end of the function. */
    PyObject * disposition = NULL;
    PyObject * plugin = NULL;
    PyObject * plugin_name = NULL;
    PyObject * next_tracename = NULL;
#ifdef RESUME
    PyObject * pCode = NULL;
#endif

    /* Borrowed references. */
    PyObject * filename = NULL;
    PyObject * disp_trace = NULL;
    PyObject * tracename = NULL;
    PyObject * file_tracer = NULL;
    PyObject * has_dynamic_filename = NULL;

    CFileDisposition * pdisp = NULL;

    STATS( self->stats.calls++; )

    /* Grow the stack. */
    if (CTracer_set_pdata_stack(self) < 0) {
        goto error;
    }
    if (DataStack_grow(&self->stats, self->pdata_stack) < 0) {
        goto error;
    }
    self->pcur_entry = &self->pdata_stack->stack[self->pdata_stack->depth];

    /* See if this frame begins a new context. */
    if (self->should_start_context != Py_None && self->context == Py_None) {
        PyObject * context;
        /* We're looking for our context, ask should_start_context if this is the start. */
        STATS( self->stats.start_context_calls++; )
        STATS( self->stats.pycalls++; )
        context = PyObject_CallFunctionObjArgs(self->should_start_context, frame, NULL);
        if (context == NULL) {
            goto error;
        }
        if (context != Py_None) {
            PyObject * val;
            Py_DECREF(self->context);
            self->context = context;
            self->pcur_entry->started_context = TRUE;
            STATS( self->stats.pycalls++; )
            val = PyObject_CallFunctionObjArgs(self->switch_context, context, NULL);
            if (val == NULL) {
                goto error;
            }
            Py_DECREF(val);
        }
        else {
            Py_DECREF(context);
            self->pcur_entry->started_context = FALSE;
        }
    }
    else {
        self->pcur_entry->started_context = FALSE;
    }

    /* Check if we should trace this line. */
    filename = MyFrame_GetCode(frame)->co_filename;
    disposition = PyDict_GetItem(self->should_trace_cache, filename);
    if (disposition == NULL) {
        if (PyErr_Occurred()) {
            goto error;
        }
        STATS( self->stats.files++; )

        /* We've never considered this file before. */
        /* Ask should_trace about it. */
        STATS( self->stats.pycalls++; )
        disposition = PyObject_CallFunctionObjArgs(self->should_trace, filename, frame, NULL);
        if (disposition == NULL) {
            /* An error occurred inside should_trace. */
            goto error;
        }
        if (PyDict_SetItem(self->should_trace_cache, filename, disposition) < 0) {
            goto error;
        }
    }
    else {
        Py_INCREF(disposition);
    }

    if (disposition == Py_None) {
        /* A later check_include returned false, so don't trace it. */
        disp_trace = Py_False;
    }
    else {
        /* The object we got is a CFileDisposition, use it efficiently. */
        pdisp = (CFileDisposition *) disposition;
        disp_trace = pdisp->trace;
        if (disp_trace == NULL) {
            goto error;
        }
    }

    if (disp_trace == Py_True) {
        /* If tracename is a string, then we're supposed to trace. */
        tracename = pdisp->source_filename;
        if (tracename == NULL) {
            goto error;
        }
        file_tracer = pdisp->file_tracer;
        if (file_tracer == NULL) {
            goto error;
        }
        if (file_tracer != Py_None) {
            plugin = PyObject_GetAttr(file_tracer, str__coverage_plugin);
            if (plugin == NULL) {
                goto error;
            }
            plugin_name = PyObject_GetAttr(plugin, str__coverage_plugin_name);
            if (plugin_name == NULL) {
                goto error;
            }
        }
        has_dynamic_filename = pdisp->has_dynamic_filename;
        if (has_dynamic_filename == NULL) {
            goto error;
        }
        if (has_dynamic_filename == Py_True) {
            STATS( self->stats.pycalls++; )
            next_tracename = PyObject_CallMethodObjArgs(
                file_tracer, str_dynamic_source_filename,
                tracename, frame, NULL
                );
            if (next_tracename == NULL) {
                /* An exception from the function. Alert the user with a
                 * warning and a traceback.
                 */
                CTracer_disable_plugin(self, disposition);
                /* Because we handled the error, goto ok. */
                goto ok;
            }
            tracename = next_tracename;

            if (tracename != Py_None) {
                /* Check the dynamic source filename against the include rules. */
                PyObject * included = NULL;
                int should_include;
                included = PyDict_GetItem(self->should_trace_cache, tracename);
                if (included == NULL) {
                    PyObject * should_include_bool;
                    if (PyErr_Occurred()) {
                        goto error;
                    }
                    STATS( self->stats.files++; )
                    STATS( self->stats.pycalls++; )
                    should_include_bool = PyObject_CallFunctionObjArgs(self->check_include, tracename, frame, NULL);
                    if (should_include_bool == NULL) {
                        goto error;
                    }
                    should_include = (should_include_bool == Py_True);
                    Py_DECREF(should_include_bool);
                    if (PyDict_SetItem(self->should_trace_cache, tracename, should_include ? disposition : Py_None) < 0) {
                        goto error;
                    }
                }
                else {
                    should_include = (included != Py_None);
                }
                if (!should_include) {
                    tracename = Py_None;
                }
            }
        }
    }
    else {
        tracename = Py_None;
    }

    if (tracename != Py_None) {
        PyObject * file_data = PyDict_GetItem(self->data, tracename);

        if (file_data == NULL) {
            if (PyErr_Occurred()) {
                goto error;
            }
            file_data = PySet_New(NULL);
            if (file_data == NULL) {
                goto error;
            }
            ret2 = PyDict_SetItem(self->data, tracename, file_data);
            if (ret2 < 0) {
                goto error;
            }

            /* If the disposition mentions a plugin, record that. */
            if (file_tracer != Py_None) {
                ret2 = PyDict_SetItem(self->file_tracers, tracename, plugin_name);
                if (ret2 < 0) {
                    goto error;
                }
            }
        }
        else {
            /* PyDict_GetItem gives a borrowed reference. Own it. */
            Py_INCREF(file_data);
        }

        Py_XDECREF(self->pcur_entry->file_data);
        self->pcur_entry->file_data = file_data;
        self->pcur_entry->file_tracer = file_tracer;

        SHOWLOG(PyFrame_GetLineNumber(frame), filename, "traced");
    }
    else {
        Py_XDECREF(self->pcur_entry->file_data);
        self->pcur_entry->file_data = NULL;
        self->pcur_entry->file_tracer = Py_None;
        frame->f_trace_lines = 0;
        SHOWLOG(PyFrame_GetLineNumber(frame), filename, "skipped");
    }

    self->pcur_entry->disposition = disposition;

    /* Make the frame right in case settrace(gettrace()) happens. */
    Py_INCREF(self);
    Py_XSETREF(frame->f_trace, (PyObject*)self);

    /* A call event is really a "start frame" event, and can happen for
     * re-entering a generator also.  How we tell the difference depends on
     * the version of Python.
     */
    BOOL real_call = FALSE;

#ifdef RESUME
    /*
     * The current opcode is guaranteed to be RESUME. The argument
     * determines what kind of resume it is.
     */
    pCode = MyCode_GetCode(MyFrame_GetCode(frame));
    real_call = (PyBytes_AS_STRING(pCode)[MyFrame_GetLasti(frame) + 1] == 0);
#else
    // f_lasti is -1 for a true call, and a real byte offset for a generator re-entry.
    real_call = (MyFrame_GetLasti(frame) < 0);
#endif

    if (real_call) {
        self->pcur_entry->last_line = -MyFrame_GetCode(frame)->co_firstlineno;
    }
    else {
        self->pcur_entry->last_line = PyFrame_GetLineNumber(frame);
    }

ok:
    ret = RET_OK;

error:
#ifdef RESUME
    MyCode_FreeCode(pCode);
#endif
    Py_XDECREF(next_tracename);
    Py_XDECREF(disposition);
    Py_XDECREF(plugin);
    Py_XDECREF(plugin_name);

    return ret;
}


static void
CTracer_disable_plugin(CTracer *self, PyObject * disposition)
{
    PyObject * ret;
    PyErr_Print();

    STATS( self->stats.pycalls++; )
    ret = PyObject_CallFunctionObjArgs(self->disable_plugin, disposition, NULL);
    if (ret == NULL) {
        goto error;
    }
    Py_DECREF(ret);

    return;

error:
    /* This function doesn't return a status, so if an error happens, print it,
     * but don't interrupt the flow. */
    /* PySys_WriteStderr is nicer, but is not in the public API. */
    fprintf(stderr, "Error occurred while disabling plug-in:\n");
    PyErr_Print();
}


static int
CTracer_unpack_pair(CTracer *self, PyObject *pair, int *p_one, int *p_two)
{
    int ret = RET_ERROR;
    int the_int;
    PyObject * pyint = NULL;
    int index;

    if (!PyTuple_Check(pair) || PyTuple_Size(pair) != 2) {
        PyErr_SetString(
            PyExc_TypeError,
            "line_number_range must return 2-tuple"
            );
        goto error;
    }

    for (index = 0; index < 2; index++) {
        pyint = PyTuple_GetItem(pair, index);
        if (pyint == NULL) {
            goto error;
        }
        if (pyint_as_int(pyint, &the_int) < 0) {
            goto error;
        }
        *(index == 0 ? p_one : p_two) = the_int;
    }

    ret = RET_OK;

error:
    return ret;
}

static int
CTracer_handle_line(CTracer *self, PyFrameObject *frame)
{
    int ret = RET_ERROR;
    int ret2;

    STATS( self->stats.lines++; )
    if (self->pdata_stack->depth >= 0) {
        SHOWLOG(PyFrame_GetLineNumber(frame), MyFrame_GetCode(frame)->co_filename, "line");
        if (self->pcur_entry->file_data) {
            int lineno_from = -1;
            int lineno_to = -1;

            /* We're tracing in this frame: record something. */
            if (self->pcur_entry->file_tracer != Py_None) {
                PyObject * from_to = NULL;
                STATS( self->stats.pycalls++; )
                from_to = PyObject_CallMethodObjArgs(self->pcur_entry->file_tracer, str_line_number_range, frame, NULL);
                if (from_to == NULL) {
                    CTracer_disable_plugin(self, self->pcur_entry->disposition);
                    goto ok;
                }
                ret2 = CTracer_unpack_pair(self, from_to, &lineno_from, &lineno_to);
                Py_DECREF(from_to);
                if (ret2 < 0) {
                    CTracer_disable_plugin(self, self->pcur_entry->disposition);
                    goto ok;
                }
            }
            else {
                lineno_from = lineno_to = PyFrame_GetLineNumber(frame);
            }

            if (lineno_from != -1) {
                for (; lineno_from <= lineno_to; lineno_from++) {
                    if (self->tracing_arcs) {
                        /* Tracing arcs: key is (last_line,this_line). */
                        if (CTracer_record_pair(self, self->pcur_entry->last_line, lineno_from) < 0) {
                            goto error;
                        }
                    }
                    else {
                        /* Tracing lines: key is simply this_line. */
                        PyObject * this_line = PyLong_FromLong((long)lineno_from);
                        if (this_line == NULL) {
                            goto error;
                        }

                        ret2 = PySet_Add(self->pcur_entry->file_data, this_line);
                        Py_DECREF(this_line);
                        if (ret2 < 0) {
                            goto error;
                        }
                    }

                    self->pcur_entry->last_line = lineno_from;
                }
            }
        }
    }

ok:
    ret = RET_OK;

error:

    return ret;
}

static int
CTracer_handle_return(CTracer *self, PyFrameObject *frame)
{
    int ret = RET_ERROR;

    PyObject * pCode = NULL;

    STATS( self->stats.returns++; )
    /* A near-copy of this code is above in the missing-return handler. */
    if (CTracer_set_pdata_stack(self) < 0) {
        goto error;
    }
    self->pcur_entry = &self->pdata_stack->stack[self->pdata_stack->depth];

    if (self->pdata_stack->depth >= 0) {
        if (self->tracing_arcs && self->pcur_entry->file_data) {
            BOOL real_return = FALSE;
            pCode = MyCode_GetCode(MyFrame_GetCode(frame));
            int lasti = MyFrame_GetLasti(frame);
            Py_ssize_t code_size = PyBytes_GET_SIZE(pCode);
            unsigned char * code_bytes = (unsigned char *)PyBytes_AS_STRING(pCode);
#ifdef RESUME
            if (lasti == code_size - 2) {
                real_return = TRUE;
            }
            else {
                real_return = (code_bytes[lasti + 2] != RESUME);
            }
#else
            /* Need to distinguish between RETURN_VALUE and YIELD_VALUE. Read
             * the current bytecode to see what it is.  In unusual circumstances
             * (Cython code), co_code can be the empty string, so range-check
             * f_lasti before reading the byte.
             */
            BOOL is_yield = FALSE;
            BOOL is_yield_from = FALSE;

            if (lasti < code_size) {
                is_yield = (code_bytes[lasti] == YIELD_VALUE);
                if (lasti + 2 < code_size) {
                    is_yield_from = (code_bytes[lasti + 2] == YIELD_FROM);
                }
            }
            real_return = !(is_yield || is_yield_from);
#endif
            if (real_return) {
                int first = MyFrame_GetCode(frame)->co_firstlineno;
                if (CTracer_record_pair(self, self->pcur_entry->last_line, -first) < 0) {
                    goto error;
                }
            }
        }

        /* If this frame started a context, then returning from it ends the context. */
        if (self->pcur_entry->started_context) {
            PyObject * val;
            Py_DECREF(self->context);
            self->context = Py_None;
            Py_INCREF(self->context);
            STATS( self->stats.pycalls++; )

            val = PyObject_CallFunctionObjArgs(self->switch_context, self->context, NULL);
            if (val == NULL) {
                goto error;
            }
            Py_DECREF(val);
        }

        /* Pop the stack. */
        SHOWLOG(PyFrame_GetLineNumber(frame), MyFrame_GetCode(frame)->co_filename, "return");
        self->pdata_stack->depth--;
        self->pcur_entry = &self->pdata_stack->stack[self->pdata_stack->depth];
    }

    ret = RET_OK;

error:

    MyCode_FreeCode(pCode);
    return ret;
}

/*
 * The Trace Function
 */
static int
CTracer_trace(CTracer *self, PyFrameObject *frame, int what, PyObject *arg_unused)
{
    int ret = RET_ERROR;

    #if DO_NOTHING
    return RET_OK;
    #endif

    if (!self->started) {
        /* If CTracer.stop() has been called from another thread, the tracer
           is still active in the current thread. Let's deactivate ourselves
           now. */
        PyEval_SetTrace(NULL, NULL);
        return RET_OK;
    }

    #if WHAT_LOG || TRACE_LOG
    PyObject * ascii = NULL;
    #endif

    #if WHAT_LOG
    const char * w = "XXX ";
    if (what <= (int)(sizeof(what_sym)/sizeof(const char *))) {
        w = what_sym[what];
    }
    ascii = PyUnicode_AsASCIIString(MyFrame_GetCode(frame)->co_filename);
    printf("%x trace: f:%x %s @ %s %d\n", (int)self, (int)frame, what_sym[what], PyBytes_AS_STRING(ascii), PyFrame_GetLineNumber(frame));
    Py_DECREF(ascii);
    #endif

    #if TRACE_LOG
    ascii = PyUnicode_AsASCIIString(MyFrame_GetCode(frame)->co_filename);
    if (strstr(PyBytes_AS_STRING(ascii), start_file) && PyFrame_GetLineNumber(frame) == start_line) {
        logging = TRUE;
    }
    Py_DECREF(ascii);
    #endif

    self->activity = TRUE;

    switch (what) {
    case PyTrace_CALL:
        if (CTracer_handle_call(self, frame) < 0) {
            goto error;
        }
        break;

    case PyTrace_RETURN:
        if (CTracer_handle_return(self, frame) < 0) {
            goto error;
        }
        break;

    case PyTrace_LINE:
        if (CTracer_handle_line(self, frame) < 0) {
            goto error;
        }
        break;

    default:
        STATS( self->stats.others++; )
        break;
    }

    ret = RET_OK;
    goto cleanup;

error:
    STATS( self->stats.errors++; )

cleanup:
    return ret;
}


/*
 * Python has two ways to set the trace function: sys.settrace(fn), which
 * takes a Python callable, and PyEval_SetTrace(func, obj), which takes
 * a C function and a Python object.  The way these work together is that
 * sys.settrace(pyfn) calls PyEval_SetTrace(builtin_func, pyfn), using the
 * Python callable as the object in PyEval_SetTrace.  So sys.gettrace()
 * simply returns the Python object used as the second argument to
 * PyEval_SetTrace.  So sys.gettrace() will return our self parameter, which
 * means it must be callable to be used in sys.settrace().
 *
 * So we make ourself callable, equivalent to invoking our trace function.
 *
 * To help with the process of replaying stored frames, this function has an
 * optional keyword argument:
 *
 *      def CTracer_call(frame, event, arg, lineno=0)
 *
 * If provided, the lineno argument is used as the line number, and the
 * frame's f_lineno member is ignored.
 */
static PyObject *
CTracer_call(CTracer *self, PyObject *args, PyObject *kwds)
{
    PyFrameObject *frame;
    PyObject *what_str;
    PyObject *arg;
    int lineno = 0;
    int what;
    int orig_lineno;
    PyObject *ret = NULL;
    PyObject * ascii = NULL;

    #if DO_NOTHING
    CRASH
    #endif

    static char *what_names[] = {
        "call", "exception", "line", "return",
        "c_call", "c_exception", "c_return",
        NULL
        };

    static char *kwlist[] = {"frame", "event", "arg", "lineno", NULL};

    if (!PyArg_ParseTupleAndKeywords(args, kwds, "O!O!O|i:Tracer_call", kwlist,
            &PyFrame_Type, &frame, &PyUnicode_Type, &what_str, &arg, &lineno)) {
        goto done;
    }

    /* In Python, the what argument is a string, we need to find an int
       for the C function. */
    for (what = 0; what_names[what]; what++) {
        int should_break;
        ascii = PyUnicode_AsASCIIString(what_str);
        should_break = !strcmp(PyBytes_AS_STRING(ascii), what_names[what]);
        Py_DECREF(ascii);
        if (should_break) {
            break;
        }
    }

    #if WHAT_LOG
    ascii = PyUnicode_AsASCIIString(MyFrame_GetCode(frame)->co_filename);
    printf("pytrace: %s @ %s %d\n", what_sym[what], PyBytes_AS_STRING(ascii), PyFrame_GetLineNumber(frame));
    Py_DECREF(ascii);
    #endif

    /* Save off the frame's lineno, and use the forced one, if provided. */
    orig_lineno = frame->f_lineno;
    if (lineno > 0) {
        frame->f_lineno = lineno;
    }

    /* Invoke the C function, and return ourselves. */
    if (CTracer_trace(self, frame, what, arg) == RET_OK) {
        Py_INCREF(self);
        ret = (PyObject *)self;
    }

    /* Clean up. */
    frame->f_lineno = orig_lineno;

    /* For better speed, install ourselves the C way so that future calls go
       directly to CTracer_trace, without this intermediate function.

       Only do this if this is a CALL event, since new trace functions only
       take effect then.  If we don't condition it on CALL, then we'll clobber
       the new trace function before it has a chance to get called.  To
       understand why, there are three internal values to track: frame.f_trace,
       c_tracefunc, and c_traceobj.  They are explained here:
       https://nedbatchelder.com/text/trace-function.html

       Without the conditional on PyTrace_CALL, this is what happens:

            def func():                 #   f_trace         c_tracefunc     c_traceobj
                                        #   --------------  --------------  --------------
                                        #   CTracer         CTracer.trace   CTracer
                sys.settrace(my_func)
                                        #   CTracer         trampoline      my_func
                        # Now Python calls trampoline(CTracer), which calls this function
                        # which calls PyEval_SetTrace below, setting us as the tracer again:
                                        #   CTracer         CTracer.trace   CTracer
                        # and it's as if the settrace never happened.
        */
    if (what == PyTrace_CALL) {
        PyEval_SetTrace((Py_tracefunc)CTracer_trace, (PyObject*)self);
    }

done:
    return ret;
}

static PyObject *
CTracer_start(CTracer *self, PyObject *args_unused)
{
    PyEval_SetTrace((Py_tracefunc)CTracer_trace, (PyObject*)self);
    self->started = TRUE;
    self->tracing_arcs = self->trace_arcs && PyObject_IsTrue(self->trace_arcs);

    /* start() returns a trace function usable with sys.settrace() */
    Py_INCREF(self);
    return (PyObject *)self;
}

static PyObject *
CTracer_stop(CTracer *self, PyObject *args_unused)
{
    if (self->started) {
        /* Set the started flag only. The actual call to
           PyEval_SetTrace(NULL, NULL) is delegated to the callback
           itself to ensure that it called from the right thread.
           */
        self->started = FALSE;
    }

    Py_RETURN_NONE;
}

static PyObject *
CTracer_activity(CTracer *self, PyObject *args_unused)
{
    if (self->activity) {
        Py_RETURN_TRUE;
    }
    else {
        Py_RETURN_FALSE;
    }
}

static PyObject *
CTracer_reset_activity(CTracer *self, PyObject *args_unused)
{
    self->activity = FALSE;
    Py_RETURN_NONE;
}

static PyObject *
CTracer_get_stats(CTracer *self, PyObject *args_unused)
{
#if COLLECT_STATS
    return Py_BuildValue(
        "{sI,sI,sI,sI,sI,sI,si,sI,sI,sI}",
        "calls", self->stats.calls,
        "lines", self->stats.lines,
        "returns", self->stats.returns,
        "others", self->stats.others,
        "files", self->stats.files,
        "stack_reallocs", self->stats.stack_reallocs,
        "stack_alloc", self->pdata_stack->alloc,
        "errors", self->stats.errors,
        "pycalls", self->stats.pycalls,
        "start_context_calls", self->stats.start_context_calls
        );
#else
    Py_RETURN_NONE;
#endif /* COLLECT_STATS */
}

static PyMemberDef
CTracer_members[] = {
    { "should_trace",       T_OBJECT, offsetof(CTracer, should_trace), 0,
            PyDoc_STR("Function indicating whether to trace a file.") },

    { "check_include",      T_OBJECT, offsetof(CTracer, check_include), 0,
            PyDoc_STR("Function indicating whether to include a file.") },

    { "warn",               T_OBJECT, offsetof(CTracer, warn), 0,
            PyDoc_STR("Function for issuing warnings.") },

    { "concur_id_func",     T_OBJECT, offsetof(CTracer, concur_id_func), 0,
            PyDoc_STR("Function for determining concurrency context") },

    { "data",               T_OBJECT, offsetof(CTracer, data), 0,
            PyDoc_STR("The raw dictionary of trace data.") },

    { "file_tracers",       T_OBJECT, offsetof(CTracer, file_tracers), 0,
            PyDoc_STR("Mapping from file name to plugin name.") },

    { "should_trace_cache", T_OBJECT, offsetof(CTracer, should_trace_cache), 0,
            PyDoc_STR("Dictionary caching should_trace results.") },

    { "trace_arcs",         T_OBJECT, offsetof(CTracer, trace_arcs), 0,
            PyDoc_STR("Should we trace arcs, or just lines?") },

    { "should_start_context", T_OBJECT, offsetof(CTracer, should_start_context), 0,
            PyDoc_STR("Function for starting contexts.") },

    { "switch_context",     T_OBJECT, offsetof(CTracer, switch_context), 0,
            PyDoc_STR("Function for switching to a new context.") },

    { "disable_plugin",     T_OBJECT, offsetof(CTracer, disable_plugin), 0,
            PyDoc_STR("Function for disabling a plugin.") },

    { NULL }
};

static PyMethodDef
CTracer_methods[] = {
    { "start",      (PyCFunction) CTracer_start,        METH_VARARGS,
            PyDoc_STR("Start the tracer") },

    { "stop",       (PyCFunction) CTracer_stop,         METH_VARARGS,
            PyDoc_STR("Stop the tracer") },

    { "get_stats",  (PyCFunction) CTracer_get_stats,    METH_VARARGS,
            PyDoc_STR("Get statistics about the tracing") },

    { "activity",   (PyCFunction) CTracer_activity,     METH_VARARGS,
            PyDoc_STR("Has there been any activity?") },

    { "reset_activity", (PyCFunction) CTracer_reset_activity, METH_VARARGS,
            PyDoc_STR("Reset the activity flag") },

    { NULL }
};

PyTypeObject
CTracerType = {
    PyVarObject_HEAD_INIT(NULL, 0)
    "coverage.CTracer",        /*tp_name*/
    sizeof(CTracer),           /*tp_basicsize*/
    0,                         /*tp_itemsize*/
    (destructor)CTracer_dealloc, /*tp_dealloc*/
    0,                         /*tp_print*/
    0,                         /*tp_getattr*/
    0,                         /*tp_setattr*/
    0,                         /*tp_compare*/
    0,                         /*tp_repr*/
    0,                         /*tp_as_number*/
    0,                         /*tp_as_sequence*/
    0,                         /*tp_as_mapping*/
    0,                         /*tp_hash */
    (ternaryfunc)CTracer_call, /*tp_call*/
    0,                         /*tp_str*/
    0,                         /*tp_getattro*/
    0,                         /*tp_setattro*/
    0,                         /*tp_as_buffer*/
    Py_TPFLAGS_DEFAULT | Py_TPFLAGS_BASETYPE, /*tp_flags*/
    "CTracer objects",         /* tp_doc */
    0,                         /* tp_traverse */
    0,                         /* tp_clear */
    0,                         /* tp_richcompare */
    0,                         /* tp_weaklistoffset */
    0,                         /* tp_iter */
    0,                         /* tp_iternext */
    CTracer_methods,           /* tp_methods */
    CTracer_members,           /* tp_members */
    0,                         /* tp_getset */
    0,                         /* tp_base */
    0,                         /* tp_dict */
    0,                         /* tp_descr_get */
    0,                         /* tp_descr_set */
    0,                         /* tp_dictoffset */
    (initproc)CTracer_init,    /* tp_init */
    0,                         /* tp_alloc */
    0,                         /* tp_new */
};
