"""Skeleton for 'multiprocessing' stdlib module."""


from multiprocessing.pool import Pool


class Process(object):
    def __init__(self, group=None, target=None, name=None, args=(), kwargs={}):
        self.name = ''
        self.daemon = False
        self.authkey = None
        self.exitcode = None
        self.ident = 0
        self.pid = 0
        self.sentinel = None

    def run(self):
        pass

    def start(self):
        pass

    def terminate(self):
        pass

    def join(self, timeout=None):
        pass

    def is_alive(self):
        return False


class ProcessError(Exception):
    pass


class BufferTooShort(ProcessError):
    pass


class AuthenticationError(ProcessError):
    pass


class TimeoutError(ProcessError):
    pass


class Connection(object):
    def send(self, obj):
        pass

    def recv(self):
        pass

    def fileno(self):
        return 0

    def close(self):
        pass

    def poll(self, timeout=None):
        pass

    def send_bytes(self, buffer, offset=-1, size=-1):
        pass

    def recv_bytes(self, maxlength=-1):
        pass

    def recv_bytes_into(self, buffer, offset=-1):
        pass

    def __enter__(self):
        pass

    def __exit__(self, exc_type, exc_val, exc_tb):
        pass


def Pipe(duplex=True):
    return Connection(), Connection()


class Queue(object):
    def __init__(self, maxsize=-1):
        self._maxsize = maxsize

    def qsize(self):
        return 0

    def empty(self):
        return False

    def full(self):
        return False

    def put(self, obj, block=True, timeout=None):
        pass

    def put_nowait(self, obj):
        pass

    def get(self, block=True, timeout=None):
        pass

    def get_nowait(self):
        pass

    def close(self):
        pass

    def join_thread(self):
        pass

    def cancel_join_thread(self):
        pass


class SimpleQueue(object):
    def empty(self):
        return False

    def get(self):
        pass

    def put(self, item):
        pass


class JoinableQueue(multiprocessing.Queue):
    def task_done(self):
        pass

    def join(self):
        pass


def active_children():
    """
    :rtype: list[multiprocessing.Process]
    """
    return []


def cpu_count():
    return 0


def current_process():
    """
    :rtype: multiprocessing.Process
    """
    return Process()


def freeze_support():
    pass


def get_all_start_methods():
    return []


def get_context(method=None):
    pass


def get_start_method(allow_none=False):
    pass


def set_executable(path):
    pass


def set_start_method(method):
    pass


class Barrier(object):
    def __init__(self, parties, action=None, timeout=None):
        self.parties = parties
        self.n_waiting = 0
        self.broken = False

    def wait(self, timeout=None):
        pass

    def reset(self):
        pass

    def abort(self):
        pass


class Semaphore(object):
    def __init__(self, value=1):
        pass

    def acquire(self, blocking=True, timeout=None):
        pass

    def release(self):
        pass


class BoundedSemaphore(multiprocessing.Semaphore):
    pass


class Condition(object):
    def __init__(self, lock=None):
        pass

    def acquire(self, *args):
        pass

    def release(self):
        pass

    def wait(self, timeout=None):
        pass

    def wait_for(self, predicate, timeout=None):
        pass

    def notify(self, n=1):
        pass

    def notify_all(self):
        pass


class Event(object):
    def is_set(self):
        return False

    def set(self):
        pass

    def clear(self):
        pass

    def wait(self, timeout=None):
        pass


class Lock(object):
    def acquire(self, blocking=True, timeout=-1):
        pass

    def release(self):
        pass


class RLock(object):
    def acquire(self, blocking=True, timeout=-1):
        pass

    def release(self):
        pass

    def __enter__(self):
        pass

    def __exit__(self, exc_type, exc_val, exc_tb):
        pass


def Value(typecode_or_type, *args, **kwargs):
    pass


def Array(typecode_or_type, size_or_initializer, lock=True):
    pass


def Manager():
    return multiprocessing.SyncManager()
