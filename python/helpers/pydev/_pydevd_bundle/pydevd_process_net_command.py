import os
import sys
import traceback

from _pydev_bundle import pydev_log
from _pydevd_bundle import pydevd_traceproperty, pydevd_tracing, pydevd_dont_trace
import pydevd_file_utils
from _pydevd_bundle.pydevd_breakpoints import LineBreakpoint, update_exception_hook
from _pydevd_bundle.pydevd_comm import CMD_RUN, CMD_VERSION, CMD_LIST_THREADS, CMD_THREAD_KILL, InternalTerminateThread, \
    CMD_THREAD_SUSPEND, pydevd_find_thread_by_id, CMD_THREAD_RUN, InternalRunThread, CMD_STEP_INTO, CMD_STEP_OVER, \
    CMD_STEP_RETURN, CMD_STEP_INTO_MY_CODE, InternalStepThread, CMD_RUN_TO_LINE, CMD_SET_NEXT_STATEMENT, \
    CMD_SMART_STEP_INTO, InternalSetNextStatementThread, CMD_RELOAD_CODE, ReloadCodeCommand, CMD_CHANGE_VARIABLE, \
    InternalChangeVariable, CMD_GET_VARIABLE, InternalGetVariable, CMD_GET_ARRAY, InternalGetArray, CMD_GET_COMPLETIONS, \
    InternalGetCompletions, CMD_GET_FRAME, InternalGetFrame, CMD_SET_BREAK, file_system_encoding, CMD_REMOVE_BREAK, \
    CMD_EVALUATE_EXPRESSION, CMD_EXEC_EXPRESSION, InternalEvaluateExpression, CMD_CONSOLE_EXEC, InternalConsoleExec, \
    CMD_SET_PY_EXCEPTION, CMD_GET_FILE_CONTENTS, CMD_SET_PROPERTY_TRACE, CMD_ADD_EXCEPTION_BREAK, \
    CMD_REMOVE_EXCEPTION_BREAK, CMD_LOAD_SOURCE, CMD_ADD_DJANGO_EXCEPTION_BREAK, CMD_REMOVE_DJANGO_EXCEPTION_BREAK, \
    CMD_EVALUATE_CONSOLE_EXPRESSION, InternalEvaluateConsoleExpression, InternalConsoleGetCompletions, \
    CMD_RUN_CUSTOM_OPERATION, InternalRunCustomOperation, CMD_IGNORE_THROWN_EXCEPTION_AT, CMD_ENABLE_DONT_TRACE, \
    CMD_SHOW_RETURN_VALUES, ID_TO_MEANING, CMD_GET_DESCRIPTION, InternalGetDescription, InternalLoadFullValue, \
    CMD_LOAD_FULL_VALUE
from _pydevd_bundle.pydevd_constants import get_thread_id, IS_PY3K, DebugInfoHolder, dict_keys, STATE_RUN, \
    NEXT_VALUE_SEPARATOR


def process_net_command(py_db, cmd_id, seq, text):
    '''Processes a command received from the Java side

    @param cmd_id: the id of the command
    @param seq: the sequence of the command
    @param text: the text received in the command

    @note: this method is run as a big switch... after doing some tests, it's not clear whether changing it for
    a dict id --> function call will have better performance result. A simple test with xrange(10000000) showed
    that the gains from having a fast access to what should be executed are lost because of the function call in
    a way that if we had 10 elements in the switch the if..elif are better -- but growing the number of choices
    makes the solution with the dispatch look better -- so, if this gets more than 20-25 choices at some time,
    it may be worth refactoring it (actually, reordering the ifs so that the ones used mostly come before
    probably will give better performance).
    '''
    # print(ID_TO_MEANING[str(cmd_id)], repr(text))

    py_db._main_lock.acquire()
    try:
        try:
            cmd = None
            if cmd_id == CMD_RUN:
                py_db.ready_to_run = True

            elif cmd_id == CMD_VERSION:
                # response is version number
                # ide_os should be 'WINDOWS' or 'UNIX'.
                ide_os = 'WINDOWS'

                # Breakpoints can be grouped by 'LINE' or by 'ID'.
                breakpoints_by = 'LINE'

                splitted = text.split('\t')
                if len(splitted) == 1:
                    _local_version = splitted

                elif len(splitted) == 2:
                    _local_version, ide_os = splitted

                elif len(splitted) == 3:
                    _local_version, ide_os, breakpoints_by = splitted

                if breakpoints_by == 'ID':
                    py_db._set_breakpoints_with_id = True
                else:
                    py_db._set_breakpoints_with_id = False

                pydevd_file_utils.set_ide_os(ide_os)

                cmd = py_db.cmd_factory.make_version_message(seq)

            elif cmd_id == CMD_LIST_THREADS:
                # response is a list of threads
                cmd = py_db.cmd_factory.make_list_threads_message(seq)

            elif cmd_id == CMD_THREAD_KILL:
                int_cmd = InternalTerminateThread(text)
                py_db.post_internal_command(int_cmd, text)

            elif cmd_id == CMD_THREAD_SUSPEND:
                # Yes, thread suspend is still done at this point, not through an internal command!
                t = pydevd_find_thread_by_id(text)
                if t and not hasattr(t, 'pydev_do_not_trace'):
                    additional_info = None
                    try:
                        additional_info = t.additional_info
                    except AttributeError:
                        pass  # that's ok, no info currently set

                    if additional_info is not None:
                        for frame in additional_info.iter_frames(t):
                            py_db.set_trace_for_frame_and_parents(frame, overwrite_prev_trace=True)
                            del frame

                    py_db.set_suspend(t, CMD_THREAD_SUSPEND)
                elif text.startswith('__frame__:'):
                    sys.stderr.write("Can't suspend tasklet: %s\n" % (text,))

            elif cmd_id == CMD_THREAD_RUN:
                t = pydevd_find_thread_by_id(text)
                if t:
                    t.additional_info.pydev_step_cmd = -1
                    t.additional_info.pydev_step_stop = None
                    t.additional_info.pydev_state = STATE_RUN

                elif text.startswith('__frame__:'):
                    sys.stderr.write("Can't make tasklet run: %s\n" % (text,))


            elif cmd_id == CMD_STEP_INTO or cmd_id == CMD_STEP_OVER or cmd_id == CMD_STEP_RETURN or \
                    cmd_id == CMD_STEP_INTO_MY_CODE:
                # we received some command to make a single step
                t = pydevd_find_thread_by_id(text)
                if t:
                    thread_id = get_thread_id(t)
                    int_cmd = InternalStepThread(thread_id, cmd_id)
                    py_db.post_internal_command(int_cmd, thread_id)

                elif text.startswith('__frame__:'):
                    sys.stderr.write("Can't make tasklet step command: %s\n" % (text,))


            elif cmd_id == CMD_RUN_TO_LINE or cmd_id == CMD_SET_NEXT_STATEMENT or cmd_id == CMD_SMART_STEP_INTO:
                # we received some command to make a single step
                thread_id, line, func_name = text.split('\t', 2)
                if func_name == "None":
                    # global context
                    func_name = ''
                t = pydevd_find_thread_by_id(thread_id)
                if t:
                    int_cmd = InternalSetNextStatementThread(thread_id, cmd_id, line, func_name, seq)
                    py_db.post_internal_command(int_cmd, thread_id)
                elif thread_id.startswith('__frame__:'):
                    sys.stderr.write("Can't set next statement in tasklet: %s\n" % (thread_id,))


            elif cmd_id == CMD_RELOAD_CODE:
                # we received some command to make a reload of a module
                module_name = text.strip()

                thread_id = '*'  # Any thread

                # Note: not going for the main thread because in this case it'd only do the load
                # when we stopped on a breakpoint.
                # for tid, t in py_db._running_thread_ids.items(): #Iterate in copy
                #    thread_name = t.getName()
                #
                #    print thread_name, get_thread_id(t)
                #    #Note: if possible, try to reload on the main thread
                #    if thread_name == 'MainThread':
                #        thread_id = tid

                int_cmd = ReloadCodeCommand(module_name, thread_id)
                py_db.post_internal_command(int_cmd, thread_id)


            elif cmd_id == CMD_CHANGE_VARIABLE:
                # the text is: thread\tstackframe\tFRAME|GLOBAL\tattribute_to_change\tvalue_to_change
                try:
                    thread_id, frame_id, scope, attr_and_value = text.split('\t', 3)

                    tab_index = attr_and_value.rindex('\t')
                    attr = attr_and_value[0:tab_index].replace('\t', '.')
                    value = attr_and_value[tab_index + 1:]
                    int_cmd = InternalChangeVariable(seq, thread_id, frame_id, scope, attr, value)
                    py_db.post_internal_command(int_cmd, thread_id)

                except:
                    traceback.print_exc()

            elif cmd_id == CMD_GET_VARIABLE:
                # we received some command to get a variable
                # the text is: thread_id\tframe_id\tFRAME|GLOBAL\tattributes*
                try:
                    thread_id, frame_id, scopeattrs = text.split('\t', 2)

                    if scopeattrs.find('\t') != -1:  # there are attributes beyond scope
                        scope, attrs = scopeattrs.split('\t', 1)
                    else:
                        scope, attrs = (scopeattrs, None)

                    int_cmd = InternalGetVariable(seq, thread_id, frame_id, scope, attrs)
                    py_db.post_internal_command(int_cmd, thread_id)

                except:
                    traceback.print_exc()

            elif cmd_id == CMD_GET_ARRAY:
                # we received some command to get an array variable
                # the text is: thread_id\tframe_id\tFRAME|GLOBAL\tname\ttemp\troffs\tcoffs\trows\tcols\tformat
                try:
                    roffset, coffset, rows, cols, format, thread_id, frame_id, scopeattrs  = text.split('\t', 7)

                    if scopeattrs.find('\t') != -1:  # there are attributes beyond scope
                        scope, attrs = scopeattrs.split('\t', 1)
                    else:
                        scope, attrs = (scopeattrs, None)

                    int_cmd = InternalGetArray(seq, roffset, coffset, rows, cols, format, thread_id, frame_id, scope, attrs)
                    py_db.post_internal_command(int_cmd, thread_id)

                except:
                    traceback.print_exc()

            elif cmd_id == CMD_SHOW_RETURN_VALUES:
                try:
                    show_return_values = text.split('\t')[1]
                    if int(show_return_values) == 1:
                        py_db.show_return_values = True
                    else:
                        if py_db.show_return_values:
                            # We should remove saved return values
                            py_db.remove_return_values_flag = True
                        py_db.show_return_values = False
                    pydev_log.debug("Show return values: %s\n" % py_db.show_return_values)
                except:
                    traceback.print_exc()

            elif cmd_id == CMD_LOAD_FULL_VALUE:
                try:
                    thread_id, frame_id, scopeattrs = text.split('\t', 2)
                    vars = scopeattrs.split(NEXT_VALUE_SEPARATOR)

                    int_cmd = InternalLoadFullValue(seq, thread_id, frame_id, vars)
                    py_db.post_internal_command(int_cmd, thread_id)
                except:
                    traceback.print_exc()

            elif cmd_id == CMD_GET_COMPLETIONS:
                # we received some command to get a variable
                # the text is: thread_id\tframe_id\tactivation token
                try:
                    thread_id, frame_id, scope, act_tok = text.split('\t', 3)

                    int_cmd = InternalGetCompletions(seq, thread_id, frame_id, act_tok)
                    py_db.post_internal_command(int_cmd, thread_id)

                except:
                    traceback.print_exc()
            elif cmd_id == CMD_GET_DESCRIPTION:
                try:

                    thread_id, frame_id, expression = text.split('\t', 2)
                    int_cmd = InternalGetDescription(seq, thread_id, frame_id, expression)
                    py_db.post_internal_command(int_cmd, thread_id)
                except:
                    traceback.print_exc()

            elif cmd_id == CMD_GET_FRAME:
                thread_id, frame_id, scope = text.split('\t', 2)

                int_cmd = InternalGetFrame(seq, thread_id, frame_id)
                py_db.post_internal_command(int_cmd, thread_id)

            elif cmd_id == CMD_SET_BREAK:
                # func name: 'None': match anything. Empty: match global, specified: only method context.
                # command to add some breakpoint.
                # text is file\tline. Add to breakpoints dictionary
                suspend_policy = "NONE"
                if py_db._set_breakpoints_with_id:
                    breakpoint_id, type, file, line, func_name, condition, expression = text.split('\t', 6)

                    breakpoint_id = int(breakpoint_id)
                    line = int(line)

                    # We must restore new lines and tabs as done in
                    # AbstractDebugTarget.breakpointAdded
                    condition = condition.replace("@_@NEW_LINE_CHAR@_@", '\n').\
                        replace("@_@TAB_CHAR@_@", '\t').strip()

                    expression = expression.replace("@_@NEW_LINE_CHAR@_@", '\n').\
                        replace("@_@TAB_CHAR@_@", '\t').strip()
                else:
                    #Note: this else should be removed after PyCharm migrates to setting
                    #breakpoints by id (and ideally also provides func_name).
                    type, file, line, func_name, suspend_policy, condition, expression = text.split('\t', 6)
                    # If we don't have an id given for each breakpoint, consider
                    # the id to be the line.
                    breakpoint_id = line = int(line)

                    condition = condition.replace("@_@NEW_LINE_CHAR@_@", '\n'). \
                        replace("@_@TAB_CHAR@_@", '\t').strip()

                    expression = expression.replace("@_@NEW_LINE_CHAR@_@", '\n'). \
                        replace("@_@TAB_CHAR@_@", '\t').strip()

                if not IS_PY3K:  # In Python 3, the frame object will have unicode for the file, whereas on python 2 it has a byte-array encoded with the filesystem encoding.
                    file = file.encode(file_system_encoding)

                file = pydevd_file_utils.norm_file_to_server(file)

                if not pydevd_file_utils.exists(file):
                    sys.stderr.write('pydev debugger: warning: trying to add breakpoint'\
                        ' to file that does not exist: %s (will have no effect)\n' % (file,))
                    sys.stderr.flush()


                if len(condition) <= 0 or condition is None or condition == "None":
                    condition = None

                if len(expression) <= 0 or expression is None or expression == "None":
                    expression = None

                if type == 'python-line':
                    breakpoint = LineBreakpoint(line, condition, func_name, expression, suspend_policy)
                    breakpoints = py_db.breakpoints
                    file_to_id_to_breakpoint = py_db.file_to_id_to_line_breakpoint
                    supported_type = True
                else:
                    result = None
                    plugin = py_db.get_plugin_lazy_init()
                    if plugin is not None:
                        result = plugin.add_breakpoint('add_line_breakpoint', py_db, type, file, line, condition, expression, func_name)
                    if result is not None:
                        supported_type = True
                        breakpoint, breakpoints = result
                        file_to_id_to_breakpoint = py_db.file_to_id_to_plugin_breakpoint
                    else:
                        supported_type = False

                if not supported_type:
                    raise NameError(type)

                if DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS > 0:
                    pydev_log.debug('Added breakpoint:%s - line:%s - func_name:%s\n' % (file, line, func_name.encode('utf-8')))
                    sys.stderr.flush()

                if file in file_to_id_to_breakpoint:
                    id_to_pybreakpoint = file_to_id_to_breakpoint[file]
                else:
                    id_to_pybreakpoint = file_to_id_to_breakpoint[file] = {}

                id_to_pybreakpoint[breakpoint_id] = breakpoint
                py_db.consolidate_breakpoints(file, id_to_pybreakpoint, breakpoints)
                if py_db.plugin is not None:
                    py_db.has_plugin_line_breaks = py_db.plugin.has_line_breaks()

                py_db.set_tracing_for_untraced_contexts_if_not_frame_eval(overwrite_prev_trace=True)
                py_db.enable_tracing_in_frames_while_running_if_frame_eval()

            elif cmd_id == CMD_REMOVE_BREAK:
                #command to remove some breakpoint
                #text is type\file\tid. Remove from breakpoints dictionary
                breakpoint_type, file, breakpoint_id = text.split('\t', 2)

                if not IS_PY3K:  # In Python 3, the frame object will have unicode for the file, whereas on python 2 it has a byte-array encoded with the filesystem encoding.
                    file = file.encode(file_system_encoding)

                file = pydevd_file_utils.norm_file_to_server(file)

                try:
                    breakpoint_id = int(breakpoint_id)
                except ValueError:
                    pydev_log.error('Error removing breakpoint. Expected breakpoint_id to be an int. Found: %s' % (breakpoint_id,))

                else:
                    file_to_id_to_breakpoint = None
                    if breakpoint_type == 'python-line':
                        breakpoints = py_db.breakpoints
                        file_to_id_to_breakpoint = py_db.file_to_id_to_line_breakpoint
                    elif py_db.get_plugin_lazy_init() is not None:
                        result = py_db.plugin.get_breakpoints(py_db, breakpoint_type)
                        if result is not None:
                            file_to_id_to_breakpoint = py_db.file_to_id_to_plugin_breakpoint
                            breakpoints = result

                    if file_to_id_to_breakpoint is None:
                        pydev_log.error('Error removing breakpoint. Cant handle breakpoint of type %s' % breakpoint_type)
                    else:
                        try:
                            id_to_pybreakpoint = file_to_id_to_breakpoint.get(file, {})
                            if DebugInfoHolder.DEBUG_TRACE_BREAKPOINTS > 0:
                                existing = id_to_pybreakpoint[breakpoint_id]
                                sys.stderr.write('Removed breakpoint:%s - line:%s - func_name:%s (id: %s)\n' % (
                                    file, existing.line, existing.func_name.encode('utf-8'), breakpoint_id))

                            del id_to_pybreakpoint[breakpoint_id]
                            py_db.consolidate_breakpoints(file, id_to_pybreakpoint, breakpoints)
                            if py_db.plugin is not None:
                                py_db.has_plugin_line_breaks = py_db.plugin.has_line_breaks()

                        except KeyError:
                            pydev_log.error("Error removing breakpoint: Breakpoint id not found: %s id: %s. Available ids: %s\n" % (
                                file, breakpoint_id, dict_keys(id_to_pybreakpoint)))


            elif cmd_id == CMD_EVALUATE_EXPRESSION or cmd_id == CMD_EXEC_EXPRESSION:
                #command to evaluate the given expression
                #text is: thread\tstackframe\tLOCAL\texpression
                temp_name = ""
                try:
                    thread_id, frame_id, scope, expression, trim, temp_name = text.split('\t', 5)
                except ValueError:
                    thread_id, frame_id, scope, expression, trim = text.split('\t', 4)
                int_cmd = InternalEvaluateExpression(seq, thread_id, frame_id, expression,
                    cmd_id == CMD_EXEC_EXPRESSION, int(trim) == 1, temp_name)
                py_db.post_internal_command(int_cmd, thread_id)

            elif cmd_id == CMD_CONSOLE_EXEC:
                #command to exec expression in console, in case expression is only partially valid 'False' is returned
                #text is: thread\tstackframe\tLOCAL\texpression

                thread_id, frame_id, scope, expression = text.split('\t', 3)

                int_cmd = InternalConsoleExec(seq, thread_id, frame_id, expression)
                py_db.post_internal_command(int_cmd, thread_id)

            elif cmd_id == CMD_SET_PY_EXCEPTION:
                # Command which receives set of exceptions on which user wants to break the debugger
                # text is: break_on_uncaught;break_on_caught;TypeError;ImportError;zipimport.ZipImportError;
                # This API is optional and works 'in bulk' -- it's possible
                # to get finer-grained control with CMD_ADD_EXCEPTION_BREAK/CMD_REMOVE_EXCEPTION_BREAK
                # which allows setting caught/uncaught per exception.
                #
                splitted = text.split(';')
                py_db.break_on_uncaught_exceptions = {}
                py_db.break_on_caught_exceptions = {}
                added = []
                if len(splitted) >= 4:
                    if splitted[0] == 'true':
                        break_on_uncaught = True
                    else:
                        break_on_uncaught = False

                    if splitted[1] == 'true':
                        break_on_caught = True
                    else:
                        break_on_caught = False

                    if splitted[2] == 'true':
                        py_db.break_on_exceptions_thrown_in_same_context = True
                    else:
                        py_db.break_on_exceptions_thrown_in_same_context = False

                    if splitted[3] == 'true':
                        py_db.ignore_exceptions_thrown_in_lines_with_ignore_exception = True
                    else:
                        py_db.ignore_exceptions_thrown_in_lines_with_ignore_exception = False

                    for exception_type in splitted[4:]:
                        exception_type = exception_type.strip()
                        if not exception_type:
                            continue

                        exception_breakpoint = py_db.add_break_on_exception(
                            exception_type,
                            notify_always=break_on_caught,
                            notify_on_terminate=break_on_uncaught,
                            notify_on_first_raise_only=False,
                        )
                        if exception_breakpoint is None:
                            continue
                        added.append(exception_breakpoint)

                    py_db.update_after_exceptions_added(added)
                    if break_on_caught:
                        py_db.enable_tracing_in_frames_while_running_if_frame_eval()

                else:
                    sys.stderr.write("Error when setting exception list. Received: %s\n" % (text,))

            elif cmd_id == CMD_GET_FILE_CONTENTS:

                if not IS_PY3K:  # In Python 3, the frame object will have unicode for the file, whereas on python 2 it has a byte-array encoded with the filesystem encoding.
                    text = text.encode(file_system_encoding)

                if os.path.exists(text):
                    f = open(text, 'r')
                    try:
                        source = f.read()
                    finally:
                        f.close()
                    cmd = py_db.cmd_factory.make_get_file_contents(seq, source)

            elif cmd_id == CMD_SET_PROPERTY_TRACE:
                # Command which receives whether to trace property getter/setter/deleter
                # text is feature_state(true/false);disable_getter/disable_setter/disable_deleter
                if text != "":
                    splitted = text.split(';')
                    if len(splitted) >= 3:
                        if py_db.disable_property_trace is False and splitted[0] == 'true':
                            # Replacing property by custom property only when the debugger starts
                            pydevd_traceproperty.replace_builtin_property()
                            py_db.disable_property_trace = True
                        # Enable/Disable tracing of the property getter
                        if splitted[1] == 'true':
                            py_db.disable_property_getter_trace = True
                        else:
                            py_db.disable_property_getter_trace = False
                        # Enable/Disable tracing of the property setter
                        if splitted[2] == 'true':
                            py_db.disable_property_setter_trace = True
                        else:
                            py_db.disable_property_setter_trace = False
                        # Enable/Disable tracing of the property deleter
                        if splitted[3] == 'true':
                            py_db.disable_property_deleter_trace = True
                        else:
                            py_db.disable_property_deleter_trace = False
                else:
                    # User hasn't configured any settings for property tracing
                    pass

            elif cmd_id == CMD_ADD_EXCEPTION_BREAK:
                condition = ""
                expression = ""
                if text.find('\t') != -1:
                    try:
                        exception, condition, expression, notify_always, notify_on_terminate, ignore_libraries = text.split('\t', 5)
                    except:
                        exception, notify_always, notify_on_terminate, ignore_libraries = text.split('\t', 3)
                else:
                    exception, notify_always, notify_on_terminate, ignore_libraries = text, 0, 0, 0

                condition = condition.replace("@_@NEW_LINE_CHAR@_@", '\n').replace("@_@TAB_CHAR@_@", '\t').strip()

                if len(condition) == 0 or condition == "None":
                    condition = None

                expression = expression.replace("@_@NEW_LINE_CHAR@_@", '\n').replace("@_@TAB_CHAR@_@", '\t').strip()

                if len(expression) == 0 or expression == "None":
                    expression = None

                if exception.find('-') != -1:
                    breakpoint_type, exception = exception.split('-')
                else:
                    breakpoint_type = 'python'

                if breakpoint_type == 'python':
                    if int(notify_always) == 1:
                        pydev_log.warn("Deprecated parameter: 'notify always' policy removed in PyCharm\n")
                    exception_breakpoint = py_db.add_break_on_exception(
                        exception,
                        condition=condition,
                        expression=expression,
                        notify_always=int(notify_always) > 0,
                        notify_on_terminate=int(notify_on_terminate) == 1,
                        notify_on_first_raise_only=int(notify_always) == 2,
                        ignore_libraries=int(ignore_libraries) > 0
                    )

                    if exception_breakpoint is not None:
                        py_db.update_after_exceptions_added([exception_breakpoint])
                        if notify_always:
                            py_db.enable_tracing_in_frames_while_running_if_frame_eval()
                else:
                    supported_type = False
                    plugin = py_db.get_plugin_lazy_init()
                    if plugin is not None:
                        supported_type = plugin.add_breakpoint('add_exception_breakpoint', py_db, breakpoint_type, exception)

                    if supported_type:
                        py_db.has_plugin_exception_breaks = py_db.plugin.has_exception_breaks()
                        py_db.enable_tracing_in_frames_while_running_if_frame_eval()
                    else:
                        raise NameError(breakpoint_type)



            elif cmd_id == CMD_REMOVE_EXCEPTION_BREAK:
                exception = text
                if exception.find('-') != -1:
                    exception_type, exception = exception.split('-')
                else:
                    exception_type = 'python'

                if exception_type == 'python':
                    try:
                        cp = py_db.break_on_uncaught_exceptions.copy()
                        cp.pop(exception, None)
                        py_db.break_on_uncaught_exceptions = cp

                        cp = py_db.break_on_caught_exceptions.copy()
                        cp.pop(exception, None)
                        py_db.break_on_caught_exceptions = cp
                    except:
                        pydev_log.debug("Error while removing exception %s"%sys.exc_info()[0])
                    update_exception_hook(py_db)
                else:
                    supported_type = False

                    # I.e.: no need to initialize lazy (if we didn't have it in the first place, we can't remove
                    # anything from it anyways).
                    plugin = py_db.plugin
                    if plugin is not None:
                        supported_type = plugin.remove_exception_breakpoint(py_db, exception_type, exception)

                    if supported_type:
                        py_db.has_plugin_exception_breaks = py_db.plugin.has_exception_breaks()
                    else:
                        raise NameError(exception_type)
                if len(py_db.break_on_caught_exceptions) == 0 and not py_db.has_plugin_exception_breaks:
                    py_db.disable_tracing_while_running_if_frame_eval()

            elif cmd_id == CMD_LOAD_SOURCE:
                path = text
                try:
                    f = open(path, 'r')
                    source = f.read()
                    py_db.cmd_factory.make_load_source_message(seq, source, py_db)
                except:
                    return py_db.cmd_factory.make_error_message(seq, pydevd_tracing.get_exception_traceback_str())

            elif cmd_id == CMD_ADD_DJANGO_EXCEPTION_BREAK:
                exception = text
                plugin = py_db.get_plugin_lazy_init()
                if plugin is not None:
                    plugin.add_breakpoint('add_exception_breakpoint', py_db, 'django', exception)
                    py_db.has_plugin_exception_breaks = py_db.plugin.has_exception_breaks()
                    py_db.enable_tracing_in_frames_while_running_if_frame_eval()

            elif cmd_id == CMD_REMOVE_DJANGO_EXCEPTION_BREAK:
                exception = text

                # I.e.: no need to initialize lazy (if we didn't have it in the first place, we can't remove
                # anything from it anyways).
                plugin = py_db.plugin
                if plugin is not None:
                    plugin.remove_exception_breakpoint(py_db, 'django', exception)
                    py_db.has_plugin_exception_breaks = py_db.plugin.has_exception_breaks()
                if len(py_db.break_on_caught_exceptions) == 0 and not py_db.has_plugin_exception_breaks:
                    py_db.disable_tracing_while_running_if_frame_eval()

            elif cmd_id == CMD_EVALUATE_CONSOLE_EXPRESSION:
                # Command which takes care for the debug console communication
                if text != "":
                    thread_id, frame_id, console_command = text.split('\t', 2)
                    console_command, line = console_command.split('\t')

                    if console_command == 'EVALUATE':
                        int_cmd = InternalEvaluateConsoleExpression(
                            seq, thread_id, frame_id, line, buffer_output=True)

                    elif console_command == 'EVALUATE_UNBUFFERED':
                        int_cmd = InternalEvaluateConsoleExpression(
                            seq, thread_id, frame_id, line, buffer_output=False)

                    elif console_command == 'GET_COMPLETIONS':
                        int_cmd = InternalConsoleGetCompletions(seq, thread_id, frame_id, line)

                    else:
                        raise ValueError('Unrecognized command: %s' % (console_command,))

                    py_db.post_internal_command(int_cmd, thread_id)

            elif cmd_id == CMD_RUN_CUSTOM_OPERATION:
                # Command which runs a custom operation
                if text != "":
                    try:
                        location, custom = text.split('||', 1)
                    except:
                        sys.stderr.write('Custom operation now needs a || separator. Found: %s\n' % (text,))
                        raise

                    thread_id, frame_id, scopeattrs = location.split('\t', 2)

                    if scopeattrs.find('\t') != -1:  # there are attributes beyond scope
                        scope, attrs = scopeattrs.split('\t', 1)
                    else:
                        scope, attrs = (scopeattrs, None)

                    # : style: EXECFILE or EXEC
                    # : encoded_code_or_file: file to execute or code
                    # : fname: name of function to be executed in the resulting namespace
                    style, encoded_code_or_file, fnname = custom.split('\t', 3)
                    int_cmd = InternalRunCustomOperation(seq, thread_id, frame_id, scope, attrs,
                                                         style, encoded_code_or_file, fnname)
                    py_db.post_internal_command(int_cmd, thread_id)

            elif cmd_id == CMD_IGNORE_THROWN_EXCEPTION_AT:
                if text:
                    replace = 'REPLACE:'  # Not all 3.x versions support u'REPLACE:', so, doing workaround.
                    if not IS_PY3K:
                        replace = unicode(replace)

                    if text.startswith(replace):
                        text = text[8:]
                        py_db.filename_to_lines_where_exceptions_are_ignored.clear()

                    if text:
                        for line in text.split('||'):  # Can be bulk-created (one in each line)
                            filename, line_number = line.split('|')
                            if not IS_PY3K:
                                filename = filename.encode(file_system_encoding)

                            filename = pydevd_file_utils.norm_file_to_server(filename)

                            if os.path.exists(filename):
                                lines_ignored = py_db.filename_to_lines_where_exceptions_are_ignored.get(filename)
                                if lines_ignored is None:
                                    lines_ignored = py_db.filename_to_lines_where_exceptions_are_ignored[filename] = {}
                                lines_ignored[int(line_number)] = 1
                            else:
                                sys.stderr.write('pydev debugger: warning: trying to ignore exception thrown'\
                                    ' on file that does not exist: %s (will have no effect)\n' % (filename,))

            elif cmd_id == CMD_ENABLE_DONT_TRACE:
                if text:
                    true_str = 'true'  # Not all 3.x versions support u'str', so, doing workaround.
                    if not IS_PY3K:
                        true_str = unicode(true_str)

                    mode = text.strip() == true_str
                    pydevd_dont_trace.trace_filter(mode)

            else:
                #I have no idea what this is all about
                cmd = py_db.cmd_factory.make_error_message(seq, "unexpected command " + str(cmd_id))

            if cmd is not None:
                py_db.writer.add_command(cmd)
                del cmd

        except Exception:
            traceback.print_exc()
            from _pydev_bundle.pydev_imports import StringIO
            stream = StringIO()
            traceback.print_exc(file=stream)
            cmd = py_db.cmd_factory.make_error_message(
                seq,
                "Unexpected exception in process_net_command.\nInitial params: %s. Exception: %s" % (
                    ((cmd_id, seq, text), stream.getvalue())
                )
            )

            py_db.writer.add_command(cmd)
    finally:
        py_db._main_lock.release()


