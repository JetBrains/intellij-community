"""Skeleton for 'multiprocessing.managers' stdlib module."""


import threading
import queue
import multiprocessing
import multiprocessing.managers


class BaseManager(object):
    def __init__(self, address=None, authkey=None):
        self.address = address

    def start(self, initializer=None, initargs=None):
        pass

    def get_server(self):
        pass

    def connect(self):
        pass

    def shutdown(self):
        pass

    @classmethod
    def register(cls, typeid, callable=None, proxytype=None, exposed=None,
                 method_to_typeid=None, create_method=None):
        pass

    def __enter__(self):
        pass

    def __exit__(self, exc_type, exc_val, exc_tb):
        pass


class SyncManager(multiprocessing.managers.BaseManager):
    def Barrier(self, parties, action=None, timeout=None):
        return threading.Barrier(parties, action, timeout)

    def BoundedSemaphore(self, value=None):
        return threading.BoundedSemaphore(value)

    def Condition(self, lock=None):
        return threading.Condition(lock)

    def Event(self):
        return threading.Event()

    def Lock(self):
        return threading.Lock()

    def Namespace(self):
        pass

    def Queue(self, maxsize=None):
        return queue.Queue()

    def RLock(self):
        return threading.RLock()

    def Semaphore(self, value=None):
        return threading.Semaphore(value)

    def Array(self, typecode, sequence):
        pass

    def Value(self, typecode, value):
        pass

    def dict(self, mapping_or_sequence):
        pass

    def list(self, sequence):
        pass
