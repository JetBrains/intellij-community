import linecache
import os.path
import re
import sys
import traceback  # @Reimport

# IFDEF CYTHON
# import dis
# ENDIF

from _pydev_bundle import pydev_log
from _pydevd_bundle import pydevd_dont_trace
from _pydevd_bundle import pydevd_vars
from _pydevd_bundle.pydevd_breakpoints import get_exception_breakpoint
from _pydevd_bundle.pydevd_comm_constants import (CMD_STEP_CAUGHT_EXCEPTION, CMD_STEP_RETURN, CMD_STEP_OVER, CMD_SET_BREAK,
                                                  CMD_STEP_INTO, CMD_SMART_STEP_INTO, CMD_STEP_INTO_MY_CODE, CMD_STEP_INTO_COROUTINE)
from _pydevd_bundle.pydevd_constants import STATE_SUSPEND, get_current_thread_id, STATE_RUN, dict_iter_values, IS_PY3K, \
    dict_keys, RETURN_VALUES_DICT, NO_FTRACE, IS_CPYTHON
from _pydevd_bundle.pydevd_dont_trace_files import DONT_TRACE, PYDEV_FILE
from _pydevd_bundle.pydevd_frame_utils import add_exception_to_frame, just_raised, remove_exception_from_frame, ignore_exception_trace
from _pydevd_bundle.pydevd_bytecode_utils import find_last_call_name, find_last_func_call_order
from _pydevd_bundle.pydevd_utils import get_clsname_for_code, should_stop_on_failed_test, is_exception_in_test_unit_can_be_ignored
from pydevd_file_utils import get_abs_path_real_path_and_base_from_frame, is_real_file

try:
    from inspect import CO_GENERATOR
except:
    CO_GENERATOR = 0
from _pydevd_bundle.pydevd_constants import IS_PY2

try:
    from _pydevd_bundle.pydevd_signature import send_signature_call_trace, send_signature_return_trace
except ImportError:
    def send_signature_call_trace(*args, **kwargs):
        pass

basename = os.path.basename

IGNORE_EXCEPTION_TAG = re.compile('[^#]*#.*@IgnoreException')
DEBUG_START = ('pydevd.py', '_exec')
DEBUG_START_PY3K = ('_pydev_execfile.py', 'execfile')
TRACE_PROPERTY = 'pydevd_traceproperty.py'
get_file_type = DONT_TRACE.get


def handle_breakpoint_condition(py_db, info, breakpoint, new_frame):
    condition = breakpoint.condition
    try:
        if breakpoint.handle_hit_condition(new_frame):
            return True

        if condition is None:
            return False

        return eval(condition, new_frame.f_globals, new_frame.f_locals)
    except Exception as e:
        if IS_PY2:
            # Must be bytes on py2.
            if isinstance(condition, unicode):
                condition = condition.encode('utf-8')

        if not isinstance(e, py_db.skip_print_breakpoint_exception):
            sys.stderr.write('Error while evaluating expression: %s\n' % (condition,))

            etype, value, tb = sys.exc_info()
            traceback.print_exception(etype, value, tb.tb_next)

        if not isinstance(e, py_db.skip_suspend_on_breakpoint_exception):
            try:
                # add exception_type and stacktrace into thread additional info
                etype, value, tb = sys.exc_info()
                error = ''.join(traceback.format_exception_only(etype, value))
                stack = traceback.extract_stack(f=tb.tb_frame.f_back)

                # On self.set_suspend(thread, CMD_SET_BREAK) this info will be
                # sent to the client.
                info.conditional_breakpoint_exception = \
                    ('Condition:\n' + condition + '\n\nError:\n' + error, stack)
            except:
                traceback.print_exc()
            return True

        return False

    finally:
        etype, value, tb = None, None, None


def handle_breakpoint_expression(breakpoint, info, new_frame):
    try:
        try:
            val = eval(breakpoint.expression, new_frame.f_globals, new_frame.f_locals)
        except:
            val = sys.exc_info()[1]
    finally:
        if val is not None:
            info.pydev_message = str(val)


#=======================================================================================================================
# PyDBFrame
#=======================================================================================================================
# IFDEF CYTHON
# cdef class PyDBFrame:
# ELSE
class PyDBFrame:
    '''This makes the tracing for a given frame, so, the trace_dispatch
    is used initially when we enter into a new context ('call') and then
    is reused for the entire context.
    '''
    # ENDIF

    # Note: class (and not instance) attributes.

    # Same thing in the main debugger but only considering the file contents, while the one in the main debugger
    # considers the user input (so, the actual result must be a join of both).
    filename_to_lines_where_exceptions_are_ignored = {}
    filename_to_stat_info = {}

    # IFDEF CYTHON
    # cdef tuple _args
    # cdef int should_skip
    # cdef int _bytecode_offset
    # cdef list _instructions
    # def __init__(self, tuple args):
    #     self._args = args # In the cython version we don't need to pass the frame
    #     self.should_skip = -1  # On cythonized version, put in instance.
    #     self._bytecode_offset = 0
    #     self._instructions = None
    # ELSE
    should_skip = -1  # Default value in class (put in instance on set).

    def __init__(self, args):
        # args = main_debugger, filename, base, info, t, frame
        # yeap, much faster than putting in self and then getting it from self later on
        self._args = args
        self._bytecode_offset = 0
    # ENDIF

    def set_suspend(self, *args, **kwargs):
        self._args[0].set_suspend(*args, **kwargs)

    def do_wait_suspend(self, *args, **kwargs):
        self._args[0].do_wait_suspend(*args, **kwargs)

    # IFDEF CYTHON
    # def trace_exception(self, frame, str event, arg):
    #     cdef bint should_stop;
    # ELSE
    def trace_exception(self, frame, event, arg):
        # ENDIF
        if event == 'exception':
            should_stop, frame = self.should_stop_on_exception(frame, event, arg)

            if should_stop:
                self.handle_exception(frame, event, arg)
                return self.trace_dispatch

        return self.trace_exception

    def trace_return(self, frame, event, arg):
        if event == 'return':
            main_debugger, filename = self._args[0], self._args[1]
            send_signature_return_trace(main_debugger, frame, filename, arg)
        return self.trace_return

    # IFDEF CYTHON
    # def should_stop_on_exception(self, frame, str event, arg):
    #     cdef PyDBAdditionalThreadInfo info;
    #     cdef bint flag;
    # ELSE
    def should_stop_on_exception(self, frame, event, arg):
        # ENDIF

        # main_debugger, _filename, info, _thread = self._args
        main_debugger = self._args[0]
        info = self._args[2]
        should_stop = False

        # STATE_SUSPEND = 2
        if info.pydev_state != 2:  # and breakpoint is not None:
            exception, value, trace = arg

            if trace is not None and hasattr(trace, 'tb_next'):
                # on jython trace is None on the first event and it may not have a tb_next.

                exception_breakpoint = get_exception_breakpoint(
                    exception, main_debugger.break_on_caught_exceptions)

                if exception_breakpoint is not None:
                    if exception_breakpoint.condition is not None:
                        # Always add exception to frame (must remove later after we proceed).
                        add_exception_to_frame(frame, (exception, value, trace))
                        eval_result = handle_breakpoint_condition(main_debugger, info, exception_breakpoint, frame)
                        remove_exception_from_frame(frame)
                        if not eval_result:
                            return False, frame

                    if exception_breakpoint.ignore_libraries:
                        if not main_debugger.is_exception_trace_in_project_scope(trace):
                            return False, frame

                    if ignore_exception_trace(trace):
                        return False, frame

                    was_just_raised = just_raised(trace)
                    if was_just_raised:

                        if main_debugger.skip_on_exceptions_thrown_in_same_context:
                            # Option: Don't break if an exception is caught in the same function from which it is thrown
                            return False, frame

                    if exception_breakpoint.notify_on_first_raise_only:
                        if main_debugger.skip_on_exceptions_thrown_in_same_context:
                            # In this case we never stop if it was just raised, so, to know if it was the first we
                            # need to check if we're in the 2nd method.
                            if not was_just_raised and not just_raised(trace.tb_next):
                                return False, frame  # I.e.: we stop only when we're at the caller of a method that throws an exception

                        else:
                            if not was_just_raised and not main_debugger.is_top_level_trace_in_project_scope(trace):
                                return False, frame  # I.e.: we stop only when it was just raised

                    # If it got here we should stop.
                    should_stop = True
                    try:
                        info.pydev_message = exception_breakpoint.qname
                    except:
                        info.pydev_message = exception_breakpoint.qname.encode('utf-8')

                    # Always add exception to frame (must remove later after we proceed).
                    add_exception_to_frame(frame, (exception, value, trace))

                    info.pydev_message = "python-%s" % info.pydev_message

                else:
                    # No regular exception breakpoint, let's see if some plugin handles it or if it is a test assertion error.
                    try:
                        if main_debugger.plugin is not None:
                            result = main_debugger.plugin.exception_break(main_debugger, self, frame, self._args, arg)
                            if result:
                                should_stop, frame = result
                        if main_debugger.stop_on_failed_tests and main_debugger.is_test_item_or_set_up_caller(trace) \
                                and not is_exception_in_test_unit_can_be_ignored(exception):
                            should_stop, frame = should_stop_on_failed_test(arg), frame
                            info.pydev_message = "python-AssertionError"
                    except:
                        should_stop = False

                if should_stop:
                    if exception_breakpoint is not None and exception_breakpoint.expression is not None:
                        handle_breakpoint_expression(exception_breakpoint, info, frame)

        return should_stop, frame

    def handle_exception(self, frame, event, arg):
        try:
            # We have 3 things in arg: exception type, description, traceback object
            trace_obj = arg[2]
            main_debugger = self._args[0]

            initial_trace_obj = trace_obj
            if trace_obj.tb_next is None and trace_obj.tb_frame is frame:
                # I.e.: tb_next should be only None in the context it was thrown (trace_obj.tb_frame is frame is just a double check).
                pass
            else:
                # Get the trace_obj from where the exception was raised...
                while trace_obj.tb_next is not None:
                    trace_obj = trace_obj.tb_next

            if main_debugger.ignore_exceptions_thrown_in_lines_with_ignore_exception \
                    and not main_debugger.stop_on_failed_tests:
                for check_trace_obj in (initial_trace_obj, trace_obj):
                    filename = get_abs_path_real_path_and_base_from_frame(check_trace_obj.tb_frame)[1]

                    filename_to_lines_where_exceptions_are_ignored = self.filename_to_lines_where_exceptions_are_ignored

                    lines_ignored = filename_to_lines_where_exceptions_are_ignored.get(filename)
                    if lines_ignored is None:
                        lines_ignored = filename_to_lines_where_exceptions_are_ignored[filename] = {}

                    try:
                        curr_stat = os.stat(filename)
                        curr_stat = (curr_stat.st_size, curr_stat.st_mtime)
                    except:
                        curr_stat = None

                    last_stat = self.filename_to_stat_info.get(filename)
                    if last_stat != curr_stat:
                        self.filename_to_stat_info[filename] = curr_stat
                        lines_ignored.clear()
                        try:
                            linecache.checkcache(filename)
                        except:
                            # Jython 2.1
                            linecache.checkcache()

                    from_user_input = main_debugger.filename_to_lines_where_exceptions_are_ignored.get(filename)
                    if from_user_input:
                        merged = {}
                        merged.update(lines_ignored)
                        # Override what we have with the related entries that the user entered
                        merged.update(from_user_input)
                    else:
                        merged = lines_ignored

                    exc_lineno = check_trace_obj.tb_lineno

                    # print ('lines ignored', lines_ignored)
                    # print ('user input', from_user_input)
                    # print ('merged', merged, 'curr', exc_lineno)

                    if exc_lineno not in merged:  # Note: check on merged but update lines_ignored.
                        try:
                            line = linecache.getline(filename, exc_lineno, check_trace_obj.tb_frame.f_globals)
                        except:
                            # Jython 2.1
                            line = linecache.getline(filename, exc_lineno)

                        if IGNORE_EXCEPTION_TAG.match(line) is not None:
                            lines_ignored[exc_lineno] = 1
                            return
                        else:
                            # Put in the cache saying not to ignore
                            lines_ignored[exc_lineno] = 0
                    else:
                        # Ok, dict has it already cached, so, let's check it...
                        if merged.get(exc_lineno, 0):
                            return

            thread = self._args[3]

            try:
                frame_id_to_frame = {}
                frame_id_to_frame[id(frame)] = frame
                f = trace_obj.tb_frame
                while f is not None:
                    frame_id_to_frame[id(f)] = f
                    f = f.f_back
                f = None

                thread_id = get_current_thread_id(thread)

                if main_debugger.stop_on_failed_tests:
                    # Our goal is to find the deepest frame in stack that still belongs to the project and stop there.
                    f = trace_obj.tb_frame
                    while f:
                        abs_path, _, _ = get_abs_path_real_path_and_base_from_frame(f)
                        if main_debugger.in_project_scope(abs_path):
                            frame = f
                            break
                        f = f.f_back
                    f = None

                    trace_obj = initial_trace_obj
                    while trace_obj:
                        if trace_obj.tb_frame is frame:
                            break
                        trace_obj = trace_obj.tb_next

                    add_exception_to_frame(frame, (arg[0], arg[1], trace_obj))

                pydevd_vars.add_additional_frame_by_id(thread_id, frame_id_to_frame)

                try:
                    main_debugger.send_caught_exception_stack(thread, arg, id(frame))
                    self.set_suspend(thread, CMD_STEP_CAUGHT_EXCEPTION)
                    self.do_wait_suspend(thread, frame, event, arg)
                    main_debugger.send_caught_exception_stack_proceeded(thread)

                finally:
                    pydevd_vars.remove_additional_frame_by_id(thread_id)
            except KeyboardInterrupt as e:
                raise e
            except:
                traceback.print_exc()

            main_debugger.set_trace_for_frame_and_parents(frame)
        finally:
            # Make sure the user cannot see the '__exception__' we added after we leave the suspend state.
            remove_exception_from_frame(frame)
            # Clear some local variables...
            frame = None
            trace_obj = None
            initial_trace_obj = None
            check_trace_obj = None
            f = None
            frame_id_to_frame = None
            main_debugger = None
            thread = None

    def get_func_name(self, frame):
        code_obj = frame.f_code
        func_name = code_obj.co_name
        try:
            cls_name = get_clsname_for_code(code_obj, frame)
            if cls_name is not None:
                return "%s.%s" % (cls_name, func_name)
            else:
                return func_name
        except:
            traceback.print_exc()
            return func_name

    def manage_return_values(self, main_debugger, frame, event, arg):

        def get_func_name(frame):
            code_obj = frame.f_code
            func_name = code_obj.co_name
            try:
                cls_name = get_clsname_for_code(code_obj, frame)
                if cls_name is not None:
                    return "%s.%s" % (cls_name, func_name)
                else:
                    return func_name
            except:
                traceback.print_exc()
                return func_name

        try:
            if main_debugger.show_return_values:
                if event == "return" and hasattr(frame, "f_code") and hasattr(frame.f_code, "co_name"):
                    if hasattr(frame, "f_back") and hasattr(frame.f_back, "f_locals"):
                        if RETURN_VALUES_DICT not in dict_keys(frame.f_back.f_locals):
                            frame.f_back.f_locals[RETURN_VALUES_DICT] = {}
                        name = get_func_name(frame)
                        frame.f_back.f_locals[RETURN_VALUES_DICT][name] = arg
            if main_debugger.remove_return_values_flag:
                # Showing return values was turned off, we should remove them from locals dict.
                # The values can be in the current frame or in the back one
                if RETURN_VALUES_DICT in dict_keys(frame.f_locals):
                    frame.f_locals.pop(RETURN_VALUES_DICT)
                if hasattr(frame, "f_back") and hasattr(frame.f_back, "f_locals"):
                    if RETURN_VALUES_DICT in dict_keys(frame.f_back.f_locals):
                        frame.f_back.f_locals.pop(RETURN_VALUES_DICT)
                main_debugger.remove_return_values_flag = False
        except:
            main_debugger.remove_return_values_flag = False
            traceback.print_exc()

    def clear_run_state(self, info):
        info.pydev_step_stop = None
        info.pydev_step_cmd = -1
        info.pydev_state = STATE_RUN

    # IFDEF CYTHON
    # cpdef trace_dispatch(self, frame, str event, arg):
    #     cdef str filename;
    #     cdef bint is_exception_event;
    #     cdef bint has_exception_breakpoints;
    #     cdef bint can_skip;
    #     cdef PyDBAdditionalThreadInfo info;
    #     cdef int step_cmd;
    #     cdef int line;
    #     cdef bint is_line;
    #     cdef bint is_call;
    #     cdef bint is_return;
    #     cdef bint should_stop;
    #     cdef dict breakpoints_for_file;
    #     cdef str curr_func_name;
    #     cdef bint exist_result;
    #     cdef dict frame_skips_cache;
    #     cdef tuple frame_cache_key;
    #     cdef tuple line_cache_key;
    #     cdef int breakpoints_in_line_cache;
    #     cdef int breakpoints_in_frame_cache;
    #     cdef bint has_breakpoint_in_frame;
    #     cdef bint need_trace_return;
    # ELSE
    def trace_dispatch(self, frame, event, arg):
        # ENDIF

        main_debugger, filename, info, thread, frame_skips_cache, frame_cache_key = self._args

        # print('frame trace_dispatch %s %s %s %s %s' % (frame.f_lineno, frame.f_code.co_name, frame.f_code.co_filename, event, info.pydev_step_cmd))

        # The thread can be already suspended by another function, e.g. built-in breakpoint hook.
        if info.is_tracing:
            return None

        try:
            info.is_tracing = True
            line = frame.f_lineno
            line_cache_key = (frame_cache_key, line)

            if main_debugger._finish_debugging_session:
                if event != 'call': frame.f_trace = NO_FTRACE
                return None

            # IFDEF CYTHON
            # if event == 'opcode':
            #     instructions = self._get_instructions(frame)
            #     for i, inst in enumerate(instructions):
            #         if inst.offset == frame.f_lasti:
            #             opname, arg, argval = inst.opname, inst.arg, str(inst.argval)
            #             print('frame trace_dispatch %s %s %s %s %s %s %s %s' % (frame.f_lineno, frame.f_lasti, frame.f_code.co_name,
            #                                                                     frame.f_code.co_filename, event, opname, arg, argval))
            #             try:
            #                 self._bytecode_offset = instructions[i + 1].offset
            #             except IndexError:
            #                 break
            #     return self.trace_dispatch
            # ENDIF

            plugin_manager = main_debugger.plugin

            is_exception_event = event == 'exception'
            has_exception_breakpoints = main_debugger.break_on_caught_exceptions or main_debugger.has_plugin_exception_breaks \
                or main_debugger.stop_on_failed_tests

            if is_exception_event:
                if has_exception_breakpoints:
                    should_stop, frame = self.should_stop_on_exception(frame, event, arg)
                    if should_stop:
                        self.handle_exception(frame, event, arg)
                        # No need to reset frame.f_trace to keep the same trace function.
                        return self.trace_dispatch
                is_line = False
                is_return = False
                is_call = False
            else:
                is_line = event == 'line'
                is_return = event == 'return'
                is_call = event == 'call'
                if not is_line and not is_return and not is_call:
                    # Unexpected: just keep the same trace func.
                    # No need to reset frame.f_trace to keep the same trace function.
                    return self.trace_dispatch

            need_signature_trace_return = False
            if main_debugger.signature_factory is not None:
                if is_call:
                    need_signature_trace_return = send_signature_call_trace(main_debugger, frame, filename)
                elif is_return:
                    send_signature_return_trace(main_debugger, frame, filename, arg)

            stop_frame = info.pydev_step_stop
            step_cmd = info.pydev_step_cmd
            is_generator_or_coroutime = frame.f_code.co_flags & 0xa0  # 0xa0 ==  CO_GENERATOR = 0x20 | CO_COROUTINE = 0x80

            breakpoints_for_file = main_debugger.breakpoints.get(filename)

            if not is_exception_event:
                if is_generator_or_coroutime:
                    if is_return:
                        # Dealing with coroutines and generators:
                        # When in a coroutine we change the perceived event to the debugger because
                        # a call, StopIteration exception and return are usually just pausing/unpausing it.
                        returns_cache_key = (frame_cache_key, 'returns')
                        return_lines = frame_skips_cache.get(returns_cache_key)
                        if return_lines is None:
                            # Note: we're collecting the return lines by inspecting the bytecode as
                            # there are multiple returns and multiple stop iterations when awaiting and
                            # it doesn't give any clear indication when a coroutine or generator is
                            # finishing or just pausing.
                            return_lines = set()
                            for x in main_debugger.collect_return_info(frame.f_code):
                                # Note: cython does not support closures in cpdefs (so we can't use
                                # a list comprehension).
                                return_lines.add(x.return_line)

                            frame_skips_cache[returns_cache_key] = return_lines

                        if line not in return_lines:
                            # Not really a return (coroutine/generator paused).
                            return self.trace_dispatch
                    elif is_call:
                        # Don't stop when calling coroutines, we will on other event anyway if necessary.
                        return self.trace_dispatch

                can_skip = False

                if info.pydev_state == 1:  # STATE_RUN = 1
                    # we can skip if:
                    # - we have no stop marked
                    # - we should make a step return/step over and we're not in the current frame
                    # CMD_STEP_RETURN = 109, CMD_STEP_OVER = 108
                    can_skip = (step_cmd == -1 and stop_frame is None) \
                               or (step_cmd in (109, 108) and stop_frame is not frame)

                    if can_skip:
                        if plugin_manager is not None and main_debugger.has_plugin_line_breaks:
                            can_skip = not plugin_manager.can_not_skip(main_debugger, self, frame, info)

                        # CMD_STEP_OVER = 108
                        if can_skip and main_debugger.show_return_values and info.pydev_step_cmd == 108 and frame.f_back is info.pydev_step_stop:
                            # trace function for showing return values after step over
                            can_skip = False

                # Let's check to see if we are in a function that has a breakpoint. If we don't have a breakpoint,
                # we will return nothing for the next trace
                # also, after we hit a breakpoint and go to some other debugging state, we have to force the set trace anyway,
                # so, that's why the additional checks are there.
                if not breakpoints_for_file:
                    if can_skip:
                        if has_exception_breakpoints:
                            frame.f_trace = self.trace_exception
                            return self.trace_exception
                        else:
                            if need_signature_trace_return:
                                frame.f_trace = self.trace_return
                                return self.trace_return
                            else:
                                if not is_call: frame.f_trace = NO_FTRACE
                                return None

                else:
                    # When cached, 0 means we don't have a breakpoint and 1 means we have.
                    if can_skip:
                        breakpoints_in_line_cache = frame_skips_cache.get(line_cache_key, -1)
                        if breakpoints_in_line_cache == 0:
                            # No need to reset frame.f_trace to keep the same trace function.
                            return self.trace_dispatch

                    breakpoints_in_frame_cache = frame_skips_cache.get(frame_cache_key, -1)
                    if breakpoints_in_frame_cache != -1:
                        # Gotten from cache.
                        has_breakpoint_in_frame = breakpoints_in_frame_cache == 1

                    else:
                        has_breakpoint_in_frame = False
                        # Checks the breakpoint to see if there is a context match in some function
                        curr_func_name = frame.f_code.co_name

                        # global context is set with an empty name
                        if curr_func_name in ('?', '<module>', '<lambda>'):
                            curr_func_name = ''

                        for breakpoint in dict_iter_values(breakpoints_for_file):  # jython does not support itervalues()
                            # will match either global or some function
                            if breakpoint.func_name in ('None', curr_func_name):
                                has_breakpoint_in_frame = True
                                break

                        # Cache the value (1 or 0 or -1 for default because of cython).
                        if has_breakpoint_in_frame:
                            frame_skips_cache[frame_cache_key] = 1
                        else:
                            frame_skips_cache[frame_cache_key] = 0

                    if can_skip and not has_breakpoint_in_frame:
                        if has_exception_breakpoints:
                            frame.f_trace = self.trace_exception
                            return self.trace_exception
                        else:
                            if need_signature_trace_return:
                                frame.f_trace = self.trace_return
                                return self.trace_return
                            else:
                                if not is_call: frame.f_trace = NO_FTRACE
                                return None

            # We may have hit a breakpoint or we are already in step mode. Either way, let's check what we should do in this frame
            # print('NOT skipped: %s %s %s %s' % (frame.f_lineno, frame.f_code.co_name, event, frame.__class__.__name__))
            try:
                flag = False
                # return is not taken into account for breakpoint hit because we'd have a double-hit in this case
                # (one for the line and the other for the return).

                stop_info = {}
                breakpoint = None
                exist_result = False
                stop = False
                bp_type = None
                smart_stop_frame = info.pydev_smart_step_context.smart_step_stop
                context_start_line = info.pydev_smart_step_context.start_line
                context_end_line = info.pydev_smart_step_context.end_line
                is_within_context = context_start_line <= line <= context_end_line

                if not is_return and info.pydev_state != STATE_SUSPEND and breakpoints_for_file is not None and line in breakpoints_for_file:
                    breakpoint = breakpoints_for_file[line]
                    new_frame = frame
                    stop = True
                    if step_cmd == CMD_STEP_OVER:
                        if stop_frame is frame and (is_line or is_return):
                            stop = False  # we don't stop on breakpoint if we have to stop by step-over (it will be processed later)
                        elif is_generator_or_coroutime and frame.f_back and frame.f_back is stop_frame:
                            stop = False  # we don't stop on breakpoint if stepping is active and we enter a `genexpr` or coroutine context
                    elif step_cmd == CMD_SMART_STEP_INTO and (frame.f_back is smart_stop_frame and is_within_context):
                        stop = False
                elif plugin_manager is not None and main_debugger.has_plugin_line_breaks:
                    result = plugin_manager.get_breakpoint(main_debugger, self, frame, event, self._args)
                    if result:
                        exist_result = True
                        flag, breakpoint, new_frame, bp_type = result

                if breakpoint:
                    # ok, hit breakpoint, now, we have to discover if it is a conditional breakpoint
                    # lets do the conditional stuff here
                    if stop or exist_result:
                        eval_result = False
                        if breakpoint.has_condition:
                            eval_result = handle_breakpoint_condition(main_debugger, info, breakpoint, new_frame)

                        if breakpoint.expression is not None:
                            handle_breakpoint_expression(breakpoint, info, new_frame)
                            if breakpoint.is_logpoint and info.pydev_message is not None and len(info.pydev_message) > 0:
                                cmd = main_debugger.cmd_factory.make_io_message(info.pydev_message + os.linesep, '1')
                                main_debugger.writer.add_command(cmd)

                        if breakpoint.has_condition and not eval_result:
                            # No need to reset frame.f_trace to keep the same trace function.
                            return self.trace_dispatch

                    if is_call and frame.f_code.co_name in ('<module>', '<lambda>'):
                        # If we find a call for a module, it means that the module is being imported/executed for the
                        # first time. In this case we have to ignore this hit as it may later duplicated by a
                        # line event at the same place (so, if there's a module with a print() in the first line
                        # the user will hit that line twice, which is not what we want).
                        #
                        # As for lambda, as it only has a single statement, it's not interesting to trace
                        # its call and later its line event as they're usually in the same line.

                        # No need to reset frame.f_trace to keep the same trace function.
                        return self.trace_dispatch

                else:
                    # if the frame is traced after breakpoint stop,
                    # but the file should be ignored while stepping because of filters
                    if step_cmd != -1:
                        if main_debugger.is_filter_enabled and main_debugger.is_ignored_by_filters(filename):
                            # ignore files matching stepping filters
                            # No need to reset frame.f_trace to keep the same trace function.
                            return self.trace_dispatch
                        if main_debugger.is_filter_libraries and not main_debugger.in_project_scope(filename):
                            # ignore library files while stepping
                            # No need to reset frame.f_trace to keep the same trace function.
                            return self.trace_dispatch

                if main_debugger.show_return_values or main_debugger.remove_return_values_flag:
                    self.manage_return_values(main_debugger, frame, event, arg)

                if stop:
                    self.set_suspend(
                        thread,
                        CMD_SET_BREAK,
                        suspend_other_threads=breakpoint and breakpoint.suspend_policy == "ALL",
                    )

                elif flag and plugin_manager is not None:
                    result = plugin_manager.suspend(main_debugger, thread, frame, bp_type)
                    if result:
                        frame = result

                # if thread has a suspend flag, we suspend with a busy wait
                if info.pydev_state == STATE_SUSPEND:
                    self.do_wait_suspend(thread, frame, event, arg)
                    # No need to reset frame.f_trace to keep the same trace function.
                    return self.trace_dispatch
                else:
                    if not breakpoint and is_line:
                        # No stop from anyone and no breakpoint found in line (cache that).
                        frame_skips_cache[line_cache_key] = 0
            except KeyboardInterrupt:
                self.clear_run_state(info)
                raise
            except:
                traceback.print_exc()
                raise

            # step handling. We stop when we hit the right frame
            try:
                should_skip = 0

                if pydevd_dont_trace.should_trace_hook is not None:
                    if self.should_skip == -1:
                        # I.e.: cache the result on self.should_skip (no need to evaluate the same frame multiple times).
                        # Note that on a code reload, we won't re-evaluate this because in practice, the frame.f_code
                        # Which will be handled by this frame is read-only, so, we can cache it safely.
                        if not pydevd_dont_trace.should_trace_hook(frame, filename):
                            # -1, 0, 1 to be Cython-friendly
                            should_skip = self.should_skip = 1
                        else:
                            should_skip = self.should_skip = 0
                    else:
                        should_skip = self.should_skip

                plugin_stop = False
                if should_skip:
                    stop = False

                elif step_cmd == CMD_SMART_STEP_INTO:
                    stop = False
                    if smart_stop_frame is frame:
                        if not is_within_context or not IS_CPYTHON:
                            # We don't stop on jumps in multiline statements, which the Python interpreter does in some cases,
                            # if we they happen in smart step into context.
                            info.pydev_func_name = '.invalid.'  # Must match the type in cython
                            stop = True  # act as if we did a step into

                    if is_line or is_exception_event:
                        curr_func_name = frame.f_code.co_name

                        # global context is set with an empty name
                        if curr_func_name in ('?', '<module>') or curr_func_name is None:
                            curr_func_name = ''

                        if smart_stop_frame and smart_stop_frame is frame.f_back:
                            if curr_func_name == info.pydev_func_name and not IS_CPYTHON:
                                # for implementations other than CPython we don't perform any additional checks
                                stop = True
                            else:
                                try:
                                    if curr_func_name != info.pydev_func_name and frame.f_back:
                                        # try to find function call name using bytecode analysis
                                        curr_func_name = find_last_call_name(frame.f_back)
                                    if curr_func_name == info.pydev_func_name:
                                        stop = find_last_func_call_order(frame.f_back, context_start_line) \
                                               == info.pydev_smart_step_context.call_order
                                except:
                                    pydev_log.debug("Exception while handling smart step into in frame tracer, step into will be performed instead.")
                                    info.pydev_smart_step_context.reset()
                                    stop = True  # act as if we did a step into

                    # we have to check this case for situations when a user has tried to step into a native function or method,
                    # e.g. `len()`, `list.append()`, etc and this was the only call in a return statement
                    if smart_stop_frame is frame and is_return:
                        stop = True

                elif step_cmd == CMD_STEP_INTO:
                    stop = is_line or is_return
                    if plugin_manager is not None:
                        result = plugin_manager.cmd_step_into(main_debugger, frame, event, self._args, stop_info, stop)
                        if result:
                            stop, plugin_stop = result

                elif step_cmd == CMD_STEP_INTO_MY_CODE:
                    if main_debugger.in_project_scope(frame.f_code.co_filename):
                        stop = is_line

                elif step_cmd in (CMD_STEP_OVER, CMD_STEP_INTO_COROUTINE):
                    stop = stop_frame is frame
                    if stop:
                        if is_line:
                            # the only case we shouldn't stop on a line, is when we traversing though asynchronous framework machinery
                            if step_cmd == CMD_STEP_INTO_COROUTINE:
                                stop = main_debugger.in_project_scope(frame.f_code.co_filename)
                        elif is_return:
                            stop = frame.f_back and main_debugger.in_project_scope(frame.f_back.f_code.co_filename)
                            if not stop:
                                back = frame.f_back
                                if back:
                                    info.pydev_step_stop = back
                                    if main_debugger.in_project_scope(frame.f_code.co_filename):
                                        # we are returning from the project scope, step over should always lead to the project scope
                                        if is_generator_or_coroutime and step_cmd == CMD_STEP_OVER:
                                            # setting ad hoc command to ensure we will skip line stops in an asynchronous framework
                                            info.pydev_step_cmd = CMD_STEP_INTO_COROUTINE
                                    else:
                                        # we were already outside the project scope because of step into or breakpoint, it's ok to stop
                                        # if we are not chopping a way through an asynchronous framework
                                        stop = not step_cmd == CMD_STEP_INTO_COROUTINE
                                else:
                                    # if there's no back frame, we just stop as soon as possible
                                    info.pydev_step_cmd = CMD_STEP_INTO
                                    info.pydev_step_stop = None
                        else:
                            stop = False

                    if CMD_STEP_OVER and plugin_manager is not None:
                        result = plugin_manager.cmd_step_over(main_debugger, frame, event, self._args, stop_info, stop)
                        if result:
                            stop, plugin_stop = result

                elif step_cmd == CMD_STEP_RETURN:
                    stop = is_return and stop_frame is frame

                else:
                    stop = False

                if stop and step_cmd != -1 and is_return and IS_PY3K and hasattr(frame, "f_back"):
                    f_code = getattr(frame.f_back, 'f_code', None)
                    if f_code is not None:
                        back_filename = os.path.basename(f_code.co_filename)
                        file_type = get_file_type(back_filename)
                        if file_type == PYDEV_FILE:
                            stop = False

                if plugin_stop:
                    stopped_on_plugin = plugin_manager.stop(main_debugger, frame, event, self._args, stop_info, arg, step_cmd)
                elif stop:
                    if is_line:
                        self.set_suspend(thread, step_cmd)
                        self.do_wait_suspend(thread, frame, event, arg)
                    else:  # return event
                        back = frame.f_back
                        if back is not None:
                            # When we get to the pydevd run function, the debugging has actually finished for the main thread
                            # (note that it can still go on for other threads, but for this one, we just make it finish)
                            # So, just setting it to None should be OK
                            _, back_filename, base = get_abs_path_real_path_and_base_from_frame(back)
                            if (base, back.f_code.co_name) in (DEBUG_START, DEBUG_START_PY3K):
                                back = None

                            elif base == TRACE_PROPERTY:
                                # We dont want to trace the return event of pydevd_traceproperty (custom property for debugging)
                                # if we're in a return, we want it to appear to the user in the previous frame!
                                if not is_call: frame.f_trace = NO_FTRACE
                                return None

                            elif pydevd_dont_trace.should_trace_hook is not None:
                                if not pydevd_dont_trace.should_trace_hook(back, back_filename):
                                    # In this case, we'll have to skip the previous one because it shouldn't be traced.
                                    # Also, we have to reset the tracing, because if the parent's parent (or some
                                    # other parent) has to be traced and it's not currently, we wouldn't stop where
                                    # we should anymore (so, a step in/over/return may not stop anywhere if no parent is traced).
                                    # Related test: _debugger_case17a.py
                                    main_debugger.set_trace_for_frame_and_parents(back)
                                    if not is_call: frame.f_trace = NO_FTRACE
                                    return None

                        if back is not None:
                            # if we're in a return, we want it to appear to the user in the previous frame!
                            self.set_suspend(thread, step_cmd)
                            self.do_wait_suspend(thread, back, event, arg)
                        else:
                            # in jython we may not have a back frame
                            self.clear_run_state(info)

            except KeyboardInterrupt:
                self.clear_run_state(info)
                raise
            except:
                try:
                    traceback.print_exc()
                    info.pydev_step_cmd = -1
                except:
                    if not is_call: frame.f_trace = NO_FTRACE
                    return None

            # if we are quitting, let's stop the tracing
            if not main_debugger.quitting:
                # No need to reset frame.f_trace to keep the same trace function.
                return self.trace_dispatch
            else:
                if not is_call: frame.f_trace = NO_FTRACE
                return None
        finally:
            info.is_tracing = False

        # end trace_dispatch

    # IFDEF CYTHON
    # cdef _get_instructions(self, frame):
    #     if self._instructions is None:
    #         self._instructions = list(dis.get_instructions(frame.f_code))
    #     return self._instructions
    # ENDIF
