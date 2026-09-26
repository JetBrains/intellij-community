from _pydev_bundle._pydev_saved_modules import ThreadingEvent, ThreadingLock, thread, threading_current_thread
from _pydevd_bundle.pydevd_daemon_thread import PyDBDaemonThread
from _pydevd_bundle.pydevd_constants import thread_get_ident, IS_CPYTHON, IS_WINDOWS, NULL
import ctypes
import time
from _pydev_bundle import pydev_log
import weakref
from _pydevd_bundle.pydevd_utils import is_current_thread_main_thread
from _pydevd_bundle import pydevd_utils

_DEBUG = False  # Default should be False as this can be very verbose.


class _TimeoutThread(PyDBDaemonThread):
    """
    The idea in this class is that it should be usually stopped waiting
    for the next event to be called (paused in a threading.Event.wait).

    When a new handle is added it sets the event so that it processes the handles and
    then keeps on waiting as needed again.

    This is done so that it's a bit more optimized than creating many Timer threads.
    """

    def __init__(self, py_db):
        PyDBDaemonThread.__init__(self, py_db)
        self._event = ThreadingEvent()
        self._handles = []

        # We could probably do things valid without this lock so that it's possible to add
        # handles while processing, but the implementation would also be harder to follow,
        # so, for now, we're either processing or adding handles, not both at the same time.
        self._lock = ThreadingLock()

    def _on_run(self):
        wait_time = None
        while not self._kill_received:
            if _DEBUG:
                if wait_time is None:
                    pydev_log.critical("pydevd_timeout: Wait until a new handle is added.")
                else:
                    pydev_log.critical("pydevd_timeout: Next wait time: %s.", wait_time)
            self._event.wait(wait_time)

            if self._kill_received:
                self._handles = []
                return

            wait_time = self.process_handles()

    def process_handles(self):
        """
        :return int:
            Returns the time we should be waiting for to process the next event properly.
        """
        with self._lock:
            if _DEBUG:
                pydev_log.critical("pydevd_timeout: Processing handles")
            self._event.clear()
            handles = self._handles
            new_handles = self._handles = []

            # Do all the processing based on this time (we want to consider snapshots
            # of processing time -- anything not processed now may be processed at the
            # next snapshot).
            curtime = time.time()

            min_handle_timeout = None

            for handle in handles:
                if curtime < handle.abs_timeout and not handle.disposed:
                    # It still didn't time out.
                    if _DEBUG:
                        pydev_log.critical("pydevd_timeout: Handle NOT processed: %s", handle)
                    new_handles.append(handle)
                    if min_handle_timeout is None:
                        min_handle_timeout = handle.abs_timeout

                    elif handle.abs_timeout < min_handle_timeout:
                        min_handle_timeout = handle.abs_timeout

                else:
                    if _DEBUG:
                        pydev_log.critical("pydevd_timeout: Handle processed: %s", handle)
                    # Timed out (or disposed), so, let's execute it (should be no-op if disposed).
                    handle.exec_on_timeout()

            if min_handle_timeout is None:
                return None
            else:
                timeout = min_handle_timeout - curtime
                if timeout <= 0:
                    pydev_log.critical("pydevd_timeout: Expected timeout to be > 0. Found: %s", timeout)

                return timeout

    def do_kill_pydev_thread(self):
        PyDBDaemonThread.do_kill_pydev_thread(self)
        with self._lock:
            self._event.set()

    def add_on_timeout_handle(self, handle):
        with self._lock:
            self._handles.append(handle)
            self._event.set()


class _OnTimeoutHandle(object):
    def __init__(self, tracker, abs_timeout, on_timeout, kwargs):
        self._str = "_OnTimeoutHandle(%s)" % (on_timeout,)

        self._tracker = weakref.ref(tracker)
        self.abs_timeout = abs_timeout
        self.on_timeout = on_timeout
        if kwargs is None:
            kwargs = {}
        self.kwargs = kwargs
        self.disposed = False

    def exec_on_timeout(self):
        # Note: lock should already be obtained when executing this function.
        kwargs = self.kwargs
        on_timeout = self.on_timeout

        if not self.disposed:
            self.disposed = True
            self.kwargs = None
            self.on_timeout = None

            try:
                if _DEBUG:
                    pydev_log.critical("pydevd_timeout: Calling on timeout: %s with kwargs: %s", on_timeout, kwargs)

                on_timeout(**kwargs)
            except Exception:
                pydev_log.exception("pydevd_timeout: Exception on callback timeout.")

    def __enter__(self):
        pass

    def __exit__(self, exc_type, exc_val, exc_tb):
        tracker = self._tracker()

        if tracker is None:
            lock = NULL
        else:
            lock = tracker._lock

        with lock:
            self.disposed = True
            self.kwargs = None
            self.on_timeout = None

    def __str__(self):
        return self._str

    __repr__ = __str__


class TimeoutTracker(object):
    """
    This is a helper class to track the timeout of something.
    """

    def __init__(self, py_db):
        self._thread = None
        self._lock = ThreadingLock()
        self._py_db = weakref.ref(py_db)

    def call_on_timeout(self, timeout, on_timeout, kwargs=None):
        """
        This can be called regularly to always execute the given function after a given timeout:

        call_on_timeout(py_db, 10, on_timeout)


        Or as a context manager to stop the method from being called if it finishes before the timeout
        elapses:

        with call_on_timeout(py_db, 10, on_timeout):
            ...

        Note: the callback will be called from a PyDBDaemonThread.
        """
        with self._lock:
            if self._thread is None:
                if _DEBUG:
                    pydev_log.critical("pydevd_timeout: Created _TimeoutThread.")

                self._thread = _TimeoutThread(self._py_db())
                self._thread.start()

            curtime = time.time()
            handle = _OnTimeoutHandle(self, curtime + timeout, on_timeout, kwargs)
            if _DEBUG:
                pydev_log.critical("pydevd_timeout: Added handle: %s.", handle)
            self._thread.add_on_timeout_handle(handle)
            return handle


def create_interrupt_this_thread_callback():
    """
    The idea here is returning a callback that when called will generate a KeyboardInterrupt
    in the thread that called this function.

    If this is the main thread, this means that it'll emulate a Ctrl+C (which may stop I/O
    and sleep operations).

    For other threads, this will call PyThreadState_SetAsyncExc to raise
    a KeyboardInterrupt before the next instruction (so, it won't really interrupt I/O or
    sleep operations).

    :return callable:
        Returns a callback that will interrupt the current thread (this may be called
        from an auxiliary thread).
    """
    tid = thread_get_ident()

    if is_current_thread_main_thread():
        main_thread = threading_current_thread()

        def raise_on_this_thread():
            pydev_log.debug("Callback to interrupt main thread.")
            pydevd_utils.interrupt_main_thread(main_thread)

    else:
        # Note: this works in the sense that it can stop some cpu-intensive slow operation,
        # but we can't really interrupt the thread out of some sleep or I/O operation
        # (this will only be raised when Python is about to execute the next instruction).
        def raise_on_this_thread():
            if IS_CPYTHON:
                pydev_log.debug("Interrupt thread: %s", tid)
                ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_long(tid), ctypes.py_object(KeyboardInterrupt))
            else:
                pydev_log.debug("It is only possible to interrupt non-main threads in CPython.")

    return raise_on_this_thread


def _can_interrupt_with_ctrl_c():
    """
    Tells whether an emulated Ctrl+C raises a KeyboardInterrupt in the main thread.

    It does so only while SIGINT still carries the handler Python installs. A debuggee which set
    a handler of its own (asyncio, Django, celery) or inherited SIG_IGN gets no KeyboardInterrupt
    from it.
    """
    try:
        import _signal

        return _signal.getsignal(_signal.SIGINT) is _signal.default_int_handler
    except Exception:
        pydev_log.exception("Unable to read the SIGINT handler.")
        return False


class _ConsoleInterrupt(object):
    """
    Raises a KeyboardInterrupt on the thread that created this object, and can drop it again
    while it is still pending.

    An emulated Ctrl+C is preferred, because it is the only one that also stops a sleep or an
    I/O operation. It is used only while it can raise a KeyboardInterrupt at all, which
    `_can_interrupt_with_ctrl_c` decides, and only for the main thread, because a signal is
    handled there and nowhere else. Every other case uses PyThreadState_SetAsyncExc, so the
    interrupt of a debug console command does not depend on the SIGINT disposition the debuggee
    ended up with.

    On Windows the Ctrl+C goes through `thread.interrupt_main`, not through
    `pydevd_utils.interrupt_main_thread`. That function calls the undocumented
    kernel32.CtrlRoutine, which does nothing when the process has no console attached, and a
    debuggee that debugpy spawned has none. It then reports success, which skips the
    `thread.interrupt_main` fallback -- the one call which works there.

    Only one of the two runs. Raising both leaves the second KeyboardInterrupt pending while
    pydevd still handles the first one, and the console then reports "During handling of the
    above exception, another exception occurred".

    Note: the asynchronous exception is raised when the target thread is about to execute its
    next Python instruction, so on that path a sleep or an I/O operation is not interrupted.
    """

    def __init__(self):
        self._tid = thread_get_ident()
        self._main_thread = threading_current_thread() if is_current_thread_main_thread() else None
        # PyThreadState_SetAsyncExc is a CPython-only API.
        self._fallback = None if IS_CPYTHON else create_interrupt_this_thread_callback()

    def __call__(self):
        if self._fallback is not None:
            self._fallback()
            return

        if self._main_thread is not None and _can_interrupt_with_ctrl_c():
            pydev_log.debug("Callback to interrupt main thread with a Ctrl+C.")
            if IS_WINDOWS:
                thread.interrupt_main()
            else:
                pydevd_utils.interrupt_main_thread(self._main_thread)
            return

        pydev_log.debug("Interrupt thread by ident: %s", self._tid)
        ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_long(self._tid), ctypes.py_object(KeyboardInterrupt))

    def cancel(self):
        """
        Drops an asynchronous exception which this object scheduled and which the target thread
        has not raised yet. May be called from any thread.

        The caller of an interrupt needs this when the evaluation ended while the interrupt was
        delivered. A KeyboardInterrupt left pending on that thread lands on a later frame,
        inside pydevd or in the resumed program, and takes the debug session down.

        A Ctrl+C cannot be taken back, so this only covers the asynchronous exception.
        """
        if self._fallback is None and IS_CPYTHON:
            ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_long(self._tid), None)


def create_interrupt_this_thread_by_ident_callback():
    """
    The idea here is the same as in `create_interrupt_this_thread_callback`, but the callback
    targets the thread which called this function even when that is the main thread, it raises
    the KeyboardInterrupt exactly once, and it can drop an interrupt that is still pending.

    :return callable:
        Returns a callback that will interrupt the thread that created it (this may be called
        from an auxiliary thread). It also has a `cancel` method -- see `_ConsoleInterrupt`.
    """
    return _ConsoleInterrupt()


def cancel_pending_interrupt_on_this_thread():
    """
    Drops a KeyboardInterrupt which the callback of
    `create_interrupt_this_thread_by_ident_callback` scheduled on this thread and which was
    not delivered yet. PyThreadState_SetAsyncExc with a NULL exception clears the pending
    asynchronous exception of the given thread.

    Must be called on the thread the callback was created on. Without it, an interrupt which
    was requested just as the evaluation finished lands on a later frame of that thread,
    inside pydevd itself, where it can only be logged and thrown away.
    """
    if IS_CPYTHON:
        ctypes.pythonapi.PyThreadState_SetAsyncExc(ctypes.c_long(thread_get_ident()), None)
