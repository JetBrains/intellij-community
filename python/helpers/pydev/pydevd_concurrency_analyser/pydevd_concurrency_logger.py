import pydevd_file_utils
from _pydev_bundle._pydev_filesystem_encoding import getfilesystemencoding
from _pydevd_bundle import pydevd_xml
from _pydevd_bundle.pydevd_constants import get_thread_id, IS_PY3K, IS_PY36_OR_GREATER
from pydevd_concurrency_analyser.pydevd_thread_wrappers import ObjectWrapper, AsyncioTaskWrapper, wrap_attr

file_system_encoding = getfilesystemencoding()

try:
    from urllib import quote
except:
    from urllib.parse import quote  # @UnresolvedImport

from _pydev_imps._pydev_saved_modules import threading
threadingCurrentThread = threading.current_thread


DONT_TRACE_THREADING = ['threading.py', 'pydevd.py']
INNER_METHODS = ['_stop']
INNER_FILES = ['threading.py']
QUEUE_MODULE = 'queue.py'
# Tread method `start` is removed, because it's being handled in `pydev_monkey` thread creation patching
THREAD_METHODS = ['_stop', 'join']
LOCK_METHODS = ['__init__', 'acquire', 'release', '__enter__', '__exit__']
QUEUE_METHODS = ['put', 'get']
ALL_METHODS = LOCK_METHODS + QUEUE_METHODS

from _pydevd_bundle.pydevd_comm import NetCommand
from _pydevd_bundle.pydevd_constants import GlobalDebuggerHolder
import traceback

import time
# return time since epoch in milliseconds
cur_time = lambda: int(round(time.time() * 1000000))


try:
    import asyncio  # @UnresolvedImport
except:
    pass


def get_text_list_for_frame(frame):
    # partial copy-paste from make_thread_suspend_str
    curFrame = frame
    cmdTextList = []
    try:
        while curFrame:
            #print cmdText
            myId = str(id(curFrame))
            #print "id is ", myId

            if curFrame.f_code is None:
                break #Iron Python sometimes does not have it!

            myName = curFrame.f_code.co_name #method name (if in method) or ? if global
            if myName is None:
                break #Iron Python sometimes does not have it!

            #print "name is ", myName

            filename = pydevd_file_utils.get_abs_path_real_path_and_base_from_frame(curFrame)[1]

            myFile = pydevd_file_utils.norm_file_to_client(filename)
            if file_system_encoding.lower() != "utf-8" and hasattr(myFile, "decode"):
                # myFile is a byte string encoded using the file system encoding
                # convert it to utf8
                myFile = myFile.decode(file_system_encoding).encode("utf-8")

            #print "file is ", myFile
            #myFile = inspect.getsourcefile(curFrame) or inspect.getfile(frame)

            myLine = str(curFrame.f_lineno)
            #print "line is ", myLine

            #the variables are all gotten 'on-demand'
            #variables = pydevd_xml.frame_vars_to_xml(curFrame.f_locals)

            variables = ''
            cmdTextList.append('<frame id="%s" name="%s" ' % (myId , pydevd_xml.make_valid_xml_value(myName)))
            cmdTextList.append('file="%s" line="%s">' % (quote(myFile, '/>_= \t'), myLine))
            cmdTextList.append(variables)
            cmdTextList.append("</frame>")
            curFrame = curFrame.f_back
    except :
        traceback.print_exc()

    return cmdTextList


def send_message(event_class, time, name, thread_id, type, event, file, line, frame, lock_id=0, parent=None):
    dbg = GlobalDebuggerHolder.global_dbg
    if dbg is None:
        return
    cmdTextList = ['<xml>']

    cmdTextList.append('<' + event_class)
    cmdTextList.append(' time="%s"' % pydevd_xml.make_valid_xml_value(str(time)))
    cmdTextList.append(' name="%s"' % pydevd_xml.make_valid_xml_value(name))
    cmdTextList.append(' thread_id="%s"' % pydevd_xml.make_valid_xml_value(thread_id))
    cmdTextList.append(' type="%s"' % pydevd_xml.make_valid_xml_value(type))
    if type == "lock":
        cmdTextList.append(' lock_id="%s"' % pydevd_xml.make_valid_xml_value(str(lock_id)))
    if parent is not None:
        cmdTextList.append(' parent="%s"' % pydevd_xml.make_valid_xml_value(parent))
    cmdTextList.append(' event="%s"' % pydevd_xml.make_valid_xml_value(event))
    cmdTextList.append(' file="%s"' % pydevd_xml.make_valid_xml_value(file))
    cmdTextList.append(' line="%s"' % pydevd_xml.make_valid_xml_value(str(line)))
    cmdTextList.append('></' + event_class + '>')

    cmdTextList += get_text_list_for_frame(frame)
    cmdTextList.append('</xml>')

    text = ''.join(cmdTextList)
    if dbg.writer is not None:
        dbg.writer.add_command(NetCommand(145, 0, text))


def log_new_thread(global_debugger, t):
    event_time = cur_time() - global_debugger.thread_analyser.start_time
    send_message("threading_event", event_time, t.getName(), get_thread_id(t), "thread",
             "start", "code_name", 0, None, parent=get_thread_id(t))


class ThreadingLogger:
    def __init__(self):
        self.start_time = cur_time()

    def set_start_time(self, time):
        self.start_time = time

    def log_event(self, frame):
        write_log = False
        self_obj = None
        if "self" in frame.f_locals:
            self_obj = frame.f_locals["self"]
            if isinstance(self_obj, threading.Thread) or self_obj.__class__ == ObjectWrapper:
                write_log = True
        if hasattr(frame, "f_back") and frame.f_back is not None:
            back = frame.f_back
            if hasattr(back, "f_back") and back.f_back is not None:
                back = back.f_back
                if "self" in back.f_locals:
                    if isinstance(back.f_locals["self"], threading.Thread):
                        write_log = True
        try:
            if write_log:
                t = threadingCurrentThread()
                back = frame.f_back
                if not back:
                    return
                _, name, back_base = pydevd_file_utils.get_abs_path_real_path_and_base_from_frame(back)
                event_time = cur_time() - self.start_time
                method_name = frame.f_code.co_name

                if isinstance(self_obj, threading.Thread):
                    if not hasattr(self_obj, "_pydev_run_patched"):
                        wrap_attr(self_obj, "run")
                    if (method_name in THREAD_METHODS) and (back_base not in DONT_TRACE_THREADING or \
                            (method_name in INNER_METHODS and back_base in INNER_FILES)):
                        thread_id = get_thread_id(self_obj)
                        name = self_obj.getName()
                        real_method = frame.f_code.co_name
                        parent = None
                        if real_method == "_stop":
                            if back_base in INNER_FILES and \
                                            back.f_code.co_name == "_wait_for_tstate_lock":
                                back = back.f_back.f_back
                            real_method = "stop"
                            if hasattr(self_obj, "_pydev_join_called"):
                                parent = get_thread_id(t)
                        elif real_method == "join":
                            # join called in the current thread, not in self object
                            if not self_obj.is_alive():
                                return
                            thread_id = get_thread_id(t)
                            name = t.getName()
                            self_obj._pydev_join_called = True

                        send_message("threading_event", event_time, name, thread_id, "thread",
                        real_method, back.f_code.co_filename, back.f_lineno, back, parent=parent)
                        # print(event_time, self_obj.getName(), thread_id, "thread",
                        #       real_method, back.f_code.co_filename, back.f_lineno)

                if method_name == "pydev_after_run_call":
                    if hasattr(frame, "f_back") and frame.f_back is not None:
                        back = frame.f_back
                        if hasattr(back, "f_back") and back.f_back is not None:
                            back = back.f_back
                        if "self" in back.f_locals:
                            if isinstance(back.f_locals["self"], threading.Thread):
                                my_self_obj = frame.f_back.f_back.f_locals["self"]
                                my_back = frame.f_back.f_back
                                my_thread_id = get_thread_id(my_self_obj)
                                send_massage = True
                                if IS_PY3K and hasattr(my_self_obj, "_pydev_join_called"):
                                    send_massage = False
                                    # we can't detect stop after join in Python 2 yet
                                if send_massage:
                                    send_message("threading_event", event_time, "Thread", my_thread_id, "thread",
                                                 "stop", my_back.f_code.co_filename, my_back.f_lineno, my_back, parent=None)

                if isinstance(self_obj, ObjectWrapper):
                    if back_base in DONT_TRACE_THREADING:
                        # do not trace methods called from threading
                        return
                    back_back_base = pydevd_file_utils.get_abs_path_real_path_and_base_from_frame(back.f_back)[-1]
                    bbb_base = None
                    bbb_frame = getattr(back.f_back, "f_back", None)
                    if bbb_frame is not None:
                        bbb_base = pydevd_file_utils.get_abs_path_real_path_and_base_from_frame(bbb_frame)[-1]
                    if back_back_base in DONT_TRACE_THREADING and bbb_base is not None and bbb_base != QUEUE_MODULE:
                        # back_back_base is the file, where the method was called froms
                        return
                    back = back.f_back

                    if method_name == "__init__":
                        send_message("threading_event", event_time, t.getName(), get_thread_id(t), "lock",
                                     method_name, back.f_code.co_filename, back.f_lineno, back, lock_id=str(id(frame.f_locals["self"])))
                    if "attr" in frame.f_locals:
                        real_method = frame.f_locals["attr"]
                        if real_method not in ALL_METHODS:
                            return
                        if method_name == "call_begin":
                            real_method += "_begin"
                        elif method_name == "call_end":
                            real_method += "_end"
                        else:
                            return
                        if real_method == "release_end":
                            # do not log release end. Maybe use it later
                            return
                        send_message("threading_event", event_time, t.getName(), get_thread_id(t), "lock",
                        real_method, back.f_code.co_filename, back.f_lineno, back, lock_id=str(id(self_obj)))
        except Exception:
            traceback.print_exc()


class NameManager:
    def __init__(self, name_prefix):
        self.tasks = {}
        self.last = 0
        self.prefix = name_prefix

    def get(self, id):
        if id not in self.tasks:
            self.last += 1
            self.tasks[id] = self.prefix + "-" + str(self.last)
        return self.tasks[id]


class AsyncioLogger:
    def __init__(self):
        self.task_mgr = NameManager("Task")
        self.start_time = cur_time()

    def get_task_id(self, frame):
        if IS_PY36_OR_GREATER:
            if "self" in frame.f_locals:
                self_obj = frame.f_locals["self"]
                current_task = asyncio.tasks._OrigTask.current_task(self_obj._loop)
                return id(current_task)
        else:
            while frame is not None:
                if "self" in frame.f_locals:
                    self_obj = frame.f_locals["self"]
                    if isinstance(self_obj,  asyncio.Task):
                        method_name = frame.f_code.co_name
                        if method_name == "_step":
                            return id(self_obj)
                frame = frame.f_back

    def log_event(self, frame):
        event_time = cur_time() - self.start_time

        if not hasattr(frame, "f_back") or frame.f_back is None:
            return
        back = frame.f_back

        if "self" in frame.f_locals:
            self_obj = frame.f_locals["self"]
            if IS_PY36_OR_GREATER:
                if self_obj.__class__ == AsyncioTaskWrapper:
                    method_name = frame.f_code.co_name
                    if method_name == "__init__" and "obj" in frame.f_locals:
                        original_task = frame.f_locals["obj"]
                        task_id = id(original_task)
                        task_name = self.task_mgr.get(str(task_id))
                        if hasattr(frame, "f_back") and hasattr(frame.f_back, "f_back"):
                            frame = frame.f_back.f_back
                            send_message("asyncio_event", event_time, task_name, task_name, "thread", "start", frame.f_code.co_filename,
                                         frame.f_lineno, frame)
                    if method_name == "call_end" and "attr" in frame.f_locals:
                        real_method = frame.f_locals["attr"]
                        if real_method == "done":
                            task_id = id(self_obj.wrapped_object)
                            task_name = self.task_mgr.get(str(task_id))
                            send_message("asyncio_event", event_time, task_name, task_name, "thread", "stop", frame.f_code.co_filename,
                                         frame.f_lineno, frame)
            else:
                if isinstance(self_obj, asyncio.Task):
                    method_name = frame.f_code.co_name
                    if method_name == "set_result":
                        task_id = id(self_obj)
                        task_name = self.task_mgr.get(str(task_id))
                        send_message("asyncio_event", event_time, task_name, task_name, "thread", "stop", frame.f_code.co_filename,
                                     frame.f_lineno, frame)

                    method_name = back.f_code.co_name
                    if method_name == "__init__":
                        task_id = id(self_obj)
                        task_name = self.task_mgr.get(str(task_id))
                        send_message("asyncio_event", event_time, task_name, task_name, "thread", "start", frame.f_code.co_filename,
                                     frame.f_lineno, frame)

            method_name = frame.f_code.co_name
            if isinstance(self_obj, asyncio.Lock):
                if method_name in ("acquire", "release"):
                    task_id = self.get_task_id(frame)
                    task_name = self.task_mgr.get(str(task_id))

                    if method_name == "acquire":
                        if not self_obj._waiters and not self_obj.locked():
                            send_message("asyncio_event", event_time, task_name, task_name, "lock",
                                         method_name+"_begin", frame.f_code.co_filename, frame.f_lineno, frame, lock_id=str(id(self_obj)))
                        if self_obj.locked():
                            method_name += "_begin"
                        else:
                            method_name += "_end"
                    elif method_name == "release":
                        method_name += "_end"

                    send_message("asyncio_event", event_time, task_name, task_name, "lock",
                                 method_name, frame.f_code.co_filename, frame.f_lineno, frame, lock_id=str(id(self_obj)))

            if isinstance(self_obj, asyncio.Queue):
                if method_name in ("put", "get", "_put", "_get"):
                    task_id = self.get_task_id(frame)
                    task_name = self.task_mgr.get(str(task_id))

                    if method_name == "put":
                        send_message("asyncio_event", event_time, task_name, task_name, "lock",
                                     "acquire_begin", frame.f_code.co_filename, frame.f_lineno, frame, lock_id=str(id(self_obj)))
                    elif method_name == "_put":
                        send_message("asyncio_event", event_time, task_name, task_name, "lock",
                                     "acquire_end", frame.f_code.co_filename, frame.f_lineno, frame, lock_id=str(id(self_obj)))
                        send_message("asyncio_event", event_time, task_name, task_name, "lock",
                                     "release", frame.f_code.co_filename, frame.f_lineno, frame, lock_id=str(id(self_obj)))
                    elif method_name == "get":
                        back = frame.f_back
                        if back.f_code.co_name != "send":
                            send_message("asyncio_event", event_time, task_name, task_name, "lock",
                                         "acquire_begin", frame.f_code.co_filename, frame.f_lineno, frame, lock_id=str(id(self_obj)))
                        else:
                            send_message("asyncio_event", event_time, task_name, task_name, "lock",
                                         "acquire_end", frame.f_code.co_filename, frame.f_lineno, frame, lock_id=str(id(self_obj)))
                            send_message("asyncio_event", event_time, task_name, task_name, "lock",
                                         "release", frame.f_code.co_filename, frame.f_lineno, frame, lock_id=str(id(self_obj)))
