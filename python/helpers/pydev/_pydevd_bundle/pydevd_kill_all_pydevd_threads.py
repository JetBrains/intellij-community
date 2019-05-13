from _pydevd_bundle.pydevd_comm import PyDBDaemonThread
from _pydevd_bundle.pydevd_constants import dict_keys

def kill_all_pydev_threads():
    threads = dict_keys(PyDBDaemonThread.created_pydb_daemon_threads)
    for t in threads:
        if hasattr(t, 'do_kill_pydev_thread'):
            t.do_kill_pydev_thread()
