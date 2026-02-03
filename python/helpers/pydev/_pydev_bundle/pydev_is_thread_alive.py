from _pydev_imps._pydev_saved_modules import threading

# Hack for https://www.brainwy.com/tracker/PyDev/363
# I.e.: calling isAlive() can throw AssertionError under some circumstances
# It is required to debug threads started by start_new_thread in Python 3.4
_temp = threading.Thread()

# Python >=3.14
if hasattr(_temp, "_os_thread_handle") and hasattr(_temp, "_started"):
    def is_thread_alive(t):
        return not t._os_thread_handle.is_done()

# Python ==3.13
elif hasattr(_temp, "_handle") and hasattr(_temp, "_started"):
    def is_thread_alive(t):
        return not t._handle.is_done()

# Python <=3.12
elif hasattr(_temp, '_is_stopped'):
    def is_thread_alive(t):
        return not t._is_stopped

# Python 2.x
elif hasattr(_temp, '_Thread__stopped'):
    def is_thread_alive(t):
        return not t._Thread__stopped

# Jython wraps a native java thread and thus only obeys the public API
elif hasattr(_temp, 'isAlive'):
    def is_thread_alive(t):
        return t.isAlive()

else:
    def is_thread_alive(t):
        raise RuntimeError('Cannot determine how to check if thread is alive')

del _temp
