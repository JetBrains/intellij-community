import os
import sys
import threading
import traceback
from os.path import splitext, basename

from _pydev_bundle import pydev_log
from _pydev_bundle.pydev_is_thread_alive import is_thread_alive
from _pydevd_bundle.pydevd_trace_dispatch import set_additional_thread_info, \
    handle_breakpoint_condition, handle_breakpoint_expression, \
    DEBUG_START, DEBUG_START_PY3K, should_stop_on_exception, handle_exception, \
    manage_return_values
from _pydevd_bundle.pydevd_breakpoints import stop_on_unhandled_exception
from _pydevd_bundle.pydevd_bytecode_utils import find_last_call_name, \
    find_last_func_call_order
from _pydevd_bundle.pydevd_comm_constants import CMD_STEP_OVER, CMD_SMART_STEP_INTO, \
    CMD_SET_BREAK, CMD_STEP_INTO, CMD_STEP_INTO_MY_CODE, CMD_STEP_INTO_COROUTINE, \
    CMD_STEP_RETURN
from _pydevd_bundle.pydevd_constants import get_current_thread_id, PYDEVD_TOOL_NAME, \
    STATE_RUN, STATE_SUSPEND
from _pydevd_bundle.pydevd_dont_trace_files import DONT_TRACE
from _pydevd_bundle.pydevd_kill_all_pydevd_threads import kill_all_pydev_threads
from pydevd_file_utils import NORM_PATHS_AND_BASE_CONTAINER, \
    get_abs_path_real_path_and_base_from_frame

threadingCurrentThread = threading.current_thread
get_file_type = DONT_TRACE.get

global_cache_skips = {}
global_cache_frame_skips = {}


class PEP669CallbackBase:
    def __init__(self, py_db):
        self.py_db = py_db

    @property
    def frame(self):
        # noinspection PyUnresolvedReferences,PyProtectedMember
        frame = sys._getframe()
        while frame and isinstance(frame.f_locals.get('self'), PEP669CallbackBase):
            frame = frame.f_back
        return frame

    @property
    def thread(self):
        return threadingCurrentThread()

    @staticmethod
    def _make_frame_cache_key(code):
        return code.co_firstlineno, code.co_name, code.co_filename

    @staticmethod
    def _get_additional_info(thread):
        # noinspection PyBroadException
        try:
            additional_info = thread.additional_info
            if additional_info is None:
                raise AttributeError()
        except:
            additional_info = set_additional_thread_info(thread)
        return additional_info

    @staticmethod
    def _get_abs_path_real_path_and_base_from_frame(frame):
        try:
            abs_path_real_path_and_base = NORM_PATHS_AND_BASE_CONTAINER[
                frame.f_code.co_filename]
        except:
            abs_path_real_path_and_base \
                = get_abs_path_real_path_and_base_from_frame(frame)

        return abs_path_real_path_and_base

    @staticmethod
    def clear_run_state(info):
        info.pydev_step_stop = None
        info.pydev_step_cmd = -1
        info.pydev_state = STATE_RUN


class PyStartCallback(PEP669CallbackBase):
    def __init__(self, py_db):
        super().__init__(py_db)
        self._line_callback = PyLineCallback(py_db)
        self._raise_callback = PyRaiseCallback(py_db)
        self._return_callback = PyReturnCallback(py_db)

    def __call__(self, code, instruction_offset):
        frame = self.frame

        # print('ENTER: PY_START ', code.co_filename, frame.f_lineno, code.co_name)

        py_db = self.py_db

        # noinspection PyBroadException
        try:
            if py_db._finish_debugging_session:
                if not py_db._termination_event_set:
                    try:
                        if py_db.output_checker_thread is None:
                            kill_all_pydev_threads()
                    except:
                        traceback.print_exc()
                    py_db._termination_event_set = True
                self.stop_monitoring()
                return

            thread = self.thread

            if not is_thread_alive(thread):
                py_db.notify_thread_not_alive(get_current_thread_id(thread))
                self.stop_monitoring()

            if py_db.thread_analyser is not None:
                py_db.thread_analyser.log_event(frame)

            if py_db.asyncio_analyser is not None:
                py_db.asyncio_analyser.log_event(frame)

            frame_cache_key = self._make_frame_cache_key(code)

            additional_info = self._get_additional_info(thread)
            pydev_step_cmd = additional_info.pydev_step_cmd
            is_stepping = pydev_step_cmd != -1

            if not is_stepping and frame_cache_key in global_cache_skips:
                # print('skipped: PY_START (cache hit)', frame_cache_key, frame.f_lineno, code.co_name)
                return

            abs_path_real_path_and_base = \
                self._get_abs_path_real_path_and_base_from_frame(frame)

            filename = abs_path_real_path_and_base[1]
            file_type = get_file_type(abs_path_real_path_and_base[-1])

            if file_type is not None:
                if file_type == 1:  # inlining LIB_FILE = 1
                    if not py_db.in_project_scope(filename):
                        # print('skipped: PY_START (not in scope)', abs_path_real_path_and_base[-1], frame.f_lineno, code.co_name, file_type)
                        global_cache_skips[frame_cache_key] = 1
                        return
                else:
                    # print('skipped: PY_START', abs_path_real_path_and_base[-1], frame.f_lineno, code.co_name, file_type)
                    global_cache_skips[frame_cache_key] = 1
                    return

            breakpoints_for_file = py_db.breakpoints.get(filename)
            if not breakpoints_for_file and not is_stepping:
                return

            if is_stepping:
                if (pydev_step_cmd == CMD_STEP_OVER
                        and frame is not additional_info.pydev_step_stop):
                    if frame.f_back is additional_info.pydev_step_stop:
                        self._return_callback.start_monitoring(code)
                    return
                if (py_db.is_filter_enabled
                        and py_db.is_ignored_by_filters(filename)):
                    return
                if (py_db.is_filter_libraries
                        and not py_db.in_project_scope(filename)):
                    return
                # We are stepping, and there is no reason to skip the frame
                # at this point.
                self._line_callback.start_monitoring(code)
                self._return_callback.start_monitoring(code)
                return

            # print('PY_START', base, frame.f_lineno, code.co_name, file_type)
            if additional_info.is_tracing:
                return

            if self._should_enable_line_events_for_code(
                    frame, code, filename, additional_info):
                self._line_callback.start_monitoring(code)
                self._return_callback.start_monitoring(code)
            else:
                global_cache_skips[frame_cache_key] = 1
                return

        except SystemExit:
            return
        except Exception:
            try:
                if traceback is not None:
                    traceback.print_exc()
            except:
                pass
            self.stop_monitoring()

    def _should_enable_line_events_for_code(self, frame, code, filename, info):
        line_number = frame.f_lineno

        # print('PY_START (should enable line events check) %s %s %s %s' % (line_number, code.co_name, filename, info.pydev_step_cmd))

        plugin_manager = self.py_db.plugin

        stop_frame = info.pydev_step_stop
        step_cmd = info.pydev_step_cmd

        breakpoints_for_file = self.py_db.breakpoints.get(filename)

        can_skip = False

        if info.pydev_state == 1:  # STATE_RUN = 1
            can_skip = (step_cmd == -1 and stop_frame is None) \
                       or (step_cmd in (109, 108) and stop_frame is not frame)

            if can_skip:
                if plugin_manager is not None and self.py_db.has_plugin_line_breaks:
                    can_skip = not plugin_manager.can_not_skip(
                        self.py_db, frame, info)

                # CMD_STEP_OVER = 108
                if (can_skip and self.py_db.show_return_values
                        and info.pydev_step_cmd == 108
                        and frame.f_back is info.pydev_step_stop):
                    # trace function for showing return values after step over
                    can_skip = False

        frame_cache_key = self._make_frame_cache_key(code)
        line_cache_key = (frame_cache_key, line_number)

        if breakpoints_for_file:
            if can_skip:
                # When cached, 0 means we don't have a breakpoint
                # and 1 means we have.
                breakpoints_in_line_cache = global_cache_frame_skips.get(
                    line_cache_key, -1)
                if breakpoints_in_line_cache == 0:
                    return False

                breakpoints_in_frame_cache = global_cache_frame_skips.get(
                    frame_cache_key, -1)
                if breakpoints_in_frame_cache != -1:
                    has_breakpoint_in_frame = breakpoints_in_frame_cache == 1
                else:
                    has_breakpoint_in_frame = False
                    # Checks the breakpoint to see if there is a context
                    # match in some function.
                    curr_func_name = frame.f_code.co_name

                    # global context is set with an empty name
                    if curr_func_name in ('?', '<module>', '<lambda>'):
                        curr_func_name = ''

                    for breakpoint in breakpoints_for_file.values():
                        # will match either global or some function
                        if breakpoint.func_name in ('None', curr_func_name):
                            has_breakpoint_in_frame = True
                            break

                    # Cache the value (1 or 0 or -1 for default because of cython).
                    if has_breakpoint_in_frame:
                        global_cache_frame_skips[frame_cache_key] = 1
                    else:
                        global_cache_frame_skips[frame_cache_key] = 0

                if can_skip and not has_breakpoint_in_frame:
                    return False

        return True

    def start_monitoring(self):
        if not sys.monitoring.get_tool(sys.monitoring.DEBUGGER_ID):
            sys.monitoring.use_tool_id(sys.monitoring.DEBUGGER_ID, PYDEVD_TOOL_NAME)
            sys.monitoring.set_events(sys.monitoring.DEBUGGER_ID,
                                      sys.monitoring.events.PY_START)
        sys.monitoring.register_callback(sys.monitoring.DEBUGGER_ID,
                                         sys.monitoring.events.PY_START, self)

        sys.monitoring.register_callback(sys.monitoring.DEBUGGER_ID,
                                         sys.monitoring.events.LINE,
                                         self._line_callback)

        sys.monitoring.register_callback(sys.monitoring.DEBUGGER_ID,
                                         sys.monitoring.events.PY_RETURN,
                                         self._return_callback)

        sys.monitoring.register_callback(sys.monitoring.DEBUGGER_ID,
                                         sys.monitoring.events.RAISE,
                                         self._raise_callback)

        # Activate exception raise callback if exception breakpoints are registered.
        current_events = sys.monitoring.get_events(sys.monitoring.DEBUGGER_ID)
        sys.monitoring.set_events(sys.monitoring.DEBUGGER_ID,
                                  current_events | sys.monitoring.events.RAISE)

    @staticmethod
    def stop_monitoring():
        sys.monitoring.set_events(sys.monitoring.DEBUGGER_ID, 0)


def enable_pep699_monitoring(py_db):
    PyStartCallback(py_db).start_monitoring()


class PyLineCallback(PEP669CallbackBase):
    def __call__(self, code, line_number):
        frame = self.frame
        thread = self.thread
        info = self._get_additional_info(thread)

        # print('LINE %s %s %s %s' % (frame.f_lineno, code.co_name, code.co_filename, info.pydev_step_cmd))

        if info.is_tracing:
            return

        try:
            info.is_tracing = True

            if self.py_db._finish_debugging_session:
                self.stop_monitoring(code)
                return

            stop_frame = info.pydev_step_stop
            step_cmd = info.pydev_step_cmd

            filename = self._get_abs_path_real_path_and_base_from_frame(frame)[1]
            breakpoints_for_file = self.py_db.breakpoints.get(filename)

            frame_cache_key = self._make_frame_cache_key(code)
            line_cache_key = (frame_cache_key, line_number)

            try:
                breakpoint = None
                stop = False
                smart_stop_frame = info.pydev_smart_step_context.smart_step_stop
                context_start_line = info.pydev_smart_step_context.start_line
                context_end_line = info.pydev_smart_step_context.end_line
                is_within_context = (context_start_line <= line_number
                                     <= context_end_line)

                if breakpoints_for_file and line_number in breakpoints_for_file:
                    breakpoint = breakpoints_for_file[line_number]
                    new_frame = frame
                    stop = True
                    if step_cmd == CMD_STEP_OVER:
                        if stop_frame is frame:
                            stop = False
                        elif step_cmd == CMD_SMART_STEP_INTO and (
                                frame.f_back is smart_stop_frame and is_within_context):
                            stop = False

                if breakpoint:
                    if stop:
                        eval_result = False
                        if breakpoint.has_condition:
                            eval_result = handle_breakpoint_condition(
                                self.py_db, info, breakpoint, new_frame)

                        if breakpoint.expression is not None:
                            handle_breakpoint_expression(breakpoint, info, new_frame)
                            if (breakpoint.is_logpoint
                                    and info.pydev_message is not None
                                    and len(info.pydev_message) > 0):
                                cmd = self.py_db.cmd_factory.make_io_message(
                                    info.pydev_message + os.linesep, '1')
                                self.py_db.writer.add_command(cmd)

                        if breakpoint.has_condition and not eval_result:
                            return
                else:
                    if step_cmd != -1:
                        if (self.py_db.is_filter_enabled
                                and self.py_db.is_ignored_by_filters(filename)):
                            # ignore files matching stepping filters
                            return
                        if (self.py_db.is_filter_libraries
                                and not self.py_db.in_project_scope(filename)):
                            # ignore library files while stepping
                            return

                if stop:
                    self.py_db.set_suspend(
                        thread,
                        CMD_SET_BREAK,
                        suspend_other_threads=breakpoint
                                              and breakpoint.suspend_policy == "ALL",
                    )

                # if thread has a suspend flag, we suspend with a busy wait
                if info.pydev_state == STATE_SUSPEND:
                    self.py_db.do_wait_suspend(thread, frame, 'line', None)
                elif not breakpoint:
                    # No stop from anyone and no breakpoint found in line (cache that).
                    global_cache_frame_skips[line_cache_key] = 0
            except KeyboardInterrupt:
                self.clear_run_state(info)
                raise
            except:
                traceback.print_exc()
                raise

            # Step handling. We stop when we hit the right frame.
            try:
                stop = False

                if step_cmd == CMD_SMART_STEP_INTO:
                    if smart_stop_frame is frame:
                        if not is_within_context:
                            # We don't stop on jumps in multiline statements, which
                            # the Python interpreter does in some cases, if we they
                            # happen in smart step into context.
                            info.pydev_func_name = '.invalid.'  # Must match the type in cython
                            stop = True  # act as if we did a step into

                    curr_func_name = frame.f_code.co_name

                    if curr_func_name in ('?', '<module>') or curr_func_name is None:
                        curr_func_name = ''

                    if smart_stop_frame and smart_stop_frame is frame.f_back:
                        try:
                            if curr_func_name != info.pydev_func_name and frame.f_back:
                                # try to find function call name using bytecode analysis
                                curr_func_name = find_last_call_name(frame.f_back)
                            if curr_func_name == info.pydev_func_name:
                                stop = (find_last_func_call_order(
                                    frame.f_back, context_start_line)
                                        == info.pydev_smart_step_context.call_order)
                        except:
                            pydev_log.debug("Exception while handling smart step into "
                                            "in frame tracer, step into will be "
                                            "performed instead.")
                            info.pydev_smart_step_context.reset()
                            stop = True  # act as if we did a step into

                elif step_cmd == CMD_STEP_INTO:
                    stop = True

                elif step_cmd == CMD_STEP_INTO_MY_CODE:
                    stop = self.py_db.in_project_scope(frame.f_code.co_filename)

                elif step_cmd in (CMD_STEP_OVER, CMD_STEP_INTO_COROUTINE):
                    stop = stop_frame is frame
                    if stop:
                        # The only case we shouldn't stop on a line, is when
                        # we are traversing though asynchronous framework machinery
                        if step_cmd == CMD_STEP_INTO_COROUTINE:
                            stop = self.py_db.in_project_scope(frame.f_code.co_filename)

                if stop:
                    self.py_db.set_suspend(thread, step_cmd)
                    self.py_db.do_wait_suspend(thread, frame, 'line', None)

            except KeyboardInterrupt:
                self.clear_run_state(info)
                raise
            except:
                traceback.print_exc()
                raise

        finally:
            info.is_tracing = False

    @staticmethod
    def start_monitoring(code):
        sys.monitoring.set_local_events(sys.monitoring.DEBUGGER_ID, code,
                                        sys.monitoring.events.LINE)

    @staticmethod
    def stop_monitoring(code):
        current_events = sys.monitoring.get_local_events(
            sys.monitoring.DEBUGGER_ID, code)
        sys.monitoring.set_local_events(
            sys.monitoring.DEBUGGER_ID, code,
            current_events ^ sys.monitoring.events.LINE)


class PyRaiseCallback(PEP669CallbackBase):
    _top_level_frame = None

    def _get_top_level_frame(self):
        if self._top_level_frame is not None:
            return self._top_level_frame

        f_unhandled = sys._getframe()

        while f_unhandled:
            filename = f_unhandled.f_code.co_filename
            name = splitext(basename(filename))[0]
            if name == 'pydevd':
                if f_unhandled.f_code.co_name == '_exec':
                    break
            f_unhandled = f_unhandled.f_back

        self._top_level_frame = f_unhandled

        return f_unhandled

    def __call__(self, code, instruction_offset, exception):
        # print('PY_RAISE %s %s %s' % (code.co_name, code.co_filename, exception))
        exc_info = (type(exception), exception, exception.__traceback__)

        frame = self.frame
        thread = self.thread
        info = self._get_additional_info(thread)

        try:
            if frame is self._get_top_level_frame():
                self._stop_on_unhandled_exception(exc_info)
                return

            has_exception_breakpoints = (self.py_db.break_on_caught_exceptions
                                         or self.py_db.has_plugin_exception_breaks
                                         or self.py_db.stop_on_failed_tests)
            if has_exception_breakpoints:
                args = (
                    self.py_db,
                    self._get_abs_path_real_path_and_base_from_frame(frame)[1],
                    self._get_additional_info(self.thread), self.thread,
                    global_cache_skips,
                    global_cache_frame_skips
                )
                should_stop, frame = should_stop_on_exception(
                    args, self.frame, 'exception', exc_info)
                if should_stop:
                    handle_exception(args, frame, 'exception', exc_info)
        except KeyboardInterrupt:
            self.clear_run_state(info)
            raise

    def _stop_on_unhandled_exception(self, exc_info):
        additional_info = self._get_additional_info(self.thread)
        if not additional_info.suspended_at_unhandled:
            additional_info.suspended_at_unhandled = True
            stop_on_unhandled_exception(self.py_db, self.thread, additional_info,
                                        exc_info)


class PyReturnCallback(PEP669CallbackBase):
    def __call__(self, code, instruction_offset, retval):
        # print('PY_RETURN %s %s %s' % (code, code.co_name, code.co_filename))

        frame = self.frame
        thread = self.thread
        info = self._get_additional_info(thread)

        try:
            if self.py_db.show_return_values or self.py_db.remove_return_values_flag:
                manage_return_values(self.py_db, frame, 'return', retval)

            step_cmd = info.pydev_step_cmd

            if step_cmd in (CMD_STEP_OVER, CMD_STEP_RETURN):
                if frame.f_back:
                    back = frame.f_back
                    back_code = back.f_code
                    if not self.py_db.in_project_scope(back_code.co_filename):
                        return
                    if back is not None:
                        _, back_filename, base \
                            = get_abs_path_real_path_and_base_from_frame(back)
                        if (base, back_code.co_name) in (DEBUG_START, DEBUG_START_PY3K):
                            back = None
                        if back is not info.pydev_step_stop:
                            self.py_db.set_suspend(thread, step_cmd)
                            self.py_db.do_wait_suspend(thread, back, 'return', retval)
                        PyLineCallback.start_monitoring(back_code)
                        if back_code.co_name != '<module>':
                            PyReturnCallback.start_monitoring(back_code)
        except KeyboardInterrupt:
            self.clear_run_state(info)
            raise
        finally:
            self.stop_monitoring(code)

    @staticmethod
    def start_monitoring(code):
        current_events = sys.monitoring.get_local_events(
            sys.monitoring.DEBUGGER_ID, code)
        sys.monitoring.set_local_events(
            sys.monitoring.DEBUGGER_ID, code,
            current_events | sys.monitoring.events.PY_RETURN)

    @staticmethod
    def stop_monitoring(code):
        current_events = sys.monitoring.get_local_events(
            sys.monitoring.DEBUGGER_ID, code)
        sys.monitoring.set_local_events(
            sys.monitoring.DEBUGGER_ID, code,
            current_events ^ sys.monitoring.events.PY_RETURN)