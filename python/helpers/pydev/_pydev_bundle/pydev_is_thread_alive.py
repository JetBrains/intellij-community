from _pydev_imps._pydev_saved_modules import threading

# Hack for https://www.brainwy.com/tracker/PyDev/363 (i.e.: calling isAlive() can throw AssertionError under some
# circumstances).
# It is required to debug threads started by start_new_thread in Python 3.4
_temp = threading.Thread()
if hasattr(_temp, '_is_stopped'): # Python 3.x has this
    def is_thread_alive(t):
        return not t._is_stopped

elif hasattr(_temp, '_Thread__stopped'): # Python 2.x has this
    def is_thread_alive(t):
        return not t._Thread__stopped

else: 
    # Make it an error: we want to detect only stops (so, isAlive() can't be used because it may return True before the
    # thread is actually running).
    raise AssertionError('Check how to detect that a thread has been stopped.')

del _temp
