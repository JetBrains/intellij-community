from _pydev_imps._pydev_saved_modules import threading

# Hack for https://sw-brainwy.rhcloud.com/tracker/PyDev/363 (i.e.: calling isAlive() can throw AssertionError under some circumstances)
# It is required to debug threads started by start_new_thread in Python 3.4
_temp = threading.Thread()
if hasattr(_temp, '_is_stopped'): # Python 3.4 has this
    def is_thread_alive(t):
        try:
            return not t._is_stopped
        except:
            return t.isAlive()

elif hasattr(_temp, '_Thread__stopped'): # Python 2.7 has this
    def is_thread_alive(t):
        try:
            return not t._Thread__stopped
        except:
            return t.isAlive()

else: # Haven't checked all other versions, so, let's use the regular isAlive call in this case.
    def is_thread_alive(t):
        return t.isAlive()
del _temp
