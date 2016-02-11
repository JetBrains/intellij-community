import sys
import weakref
from _pydev_imps import _pydev_thread
from _pydevd_bundle.pydevd_constants import STATE_RUN, PYTHON_SUSPEND, dict_iter_items
from _pydevd_bundle.pydevd_frame import PyDBFrame


#=======================================================================================================================
# PyDBAdditionalThreadInfo
#=======================================================================================================================
# IFDEF CYTHON
# cdef class PyDBAdditionalThreadInfo:
# ELSE
class PyDBAdditionalThreadInfo(object):
    # ENDIF

    # IFDEF CYTHON
    # cdef public int pydev_state;
    # cdef public object pydev_step_stop; # Actually, it's a frame or None
    # cdef public int pydev_step_cmd;
    # cdef public bint pydev_notify_kill;
    # cdef public object pydev_smart_step_stop; # Actually, it's a frame or None
    # cdef public bint pydev_django_resolve_frame;
    # cdef public object pydev_call_from_jinja2;
    # cdef public object pydev_call_inside_jinja2;
    # cdef public bint is_tracing;
    # cdef public tuple conditional_breakpoint_exception;
    # cdef public str pydev_message;
    # cdef public int suspend_type;
    # cdef public int pydev_next_line;
    # cdef public str pydev_func_name;
    # ELSE
    __slots__ = [
        'pydev_state',
        'pydev_step_stop',
        'pydev_step_cmd',
        'pydev_notify_kill',
        'pydev_smart_step_stop',
        'pydev_django_resolve_frame',
        'pydev_call_from_jinja2',
        'pydev_call_inside_jinja2',
        'is_tracing',
        'conditional_breakpoint_exception',
        'pydev_message',
        'suspend_type',
        'pydev_next_line',
        'pydev_func_name',
    ]
    # ENDIF

    def __init__(self):
        self.pydev_state = STATE_RUN
        self.pydev_step_stop = None
        self.pydev_step_cmd = -1 # Something as CMD_STEP_INTO, CMD_STEP_OVER, etc.
        self.pydev_notify_kill = False
        self.pydev_smart_step_stop = None
        self.pydev_django_resolve_frame = False
        self.pydev_call_from_jinja2 = None
        self.pydev_call_inside_jinja2 = None
        self.is_tracing = False
        self.conditional_breakpoint_exception = None
        self.pydev_message = ''
        self.suspend_type = PYTHON_SUSPEND
        self.pydev_next_line = -1
        self.pydev_func_name = '.invalid.' # Must match the type in cython


    def iter_frames(self, t):
        #sys._current_frames(): dictionary with thread id -> topmost frame
        current_frames = sys._current_frames()
        v = current_frames.get(t.ident)
        if v is not None:
            return [v]
        return []

    # IFDEF CYTHON
    # def create_db_frame(self, *args, **kwargs):
    #     raise AssertionError('This method should not be called on cython (PyDbFrame should be used directly).')
    # ELSE
    # just create the db frame directly
    create_db_frame = PyDBFrame
    # ENDIF

    def __str__(self):
        return 'State:%s Stop:%s Cmd: %s Kill:%s' % (
            self.pydev_state, self.pydev_step_stop, self.pydev_step_cmd, self.pydev_notify_kill)

#=======================================================================================================================
# Note that the Cython version has only the contents above
#=======================================================================================================================

# IFDEF CYTHON
# ELSE

PyDBAdditionalThreadInfoOriginal = PyDBAdditionalThreadInfo
#=======================================================================================================================
# PyDBAdditionalThreadInfoWithoutCurrentFramesSupport
#=======================================================================================================================
class PyDBAdditionalThreadInfoWithoutCurrentFramesSupport(PyDBAdditionalThreadInfoOriginal):

    def __init__(self):
        PyDBAdditionalThreadInfoOriginal.__init__(self)
        #That's where the last frame entered is kept. That's needed so that we're able to
        #trace contexts that were previously untraced and are currently active. So, the bad thing
        #is that the frame may be kept alive longer than it would if we go up on the frame stack,
        #and is only disposed when some other frame is removed.
        #A better way would be if we could get the topmost frame for each thread, but that's
        #not possible (until python 2.5 -- which is the PyDBAdditionalThreadInfo version)
        #Or if the user compiled threadframe (from http://www.majid.info/mylos/stories/2004/06/10/threadframe.html)

        #NOT RLock!! (could deadlock if it was)
        self.lock = _pydev_thread.allocate_lock()
        self._acquire_lock = self.lock.acquire
        self._release_lock = self.lock.release

        #collection with the refs
        d = {}
        self.pydev_existing_frames = d
        try:
            self._iter_frames = d.iterkeys
        except AttributeError:
            self._iter_frames = d.keys


    def _OnDbFrameCollected(self, ref):
        '''
            Callback to be called when a given reference is garbage-collected.
        '''
        self._acquire_lock()
        try:
            del self.pydev_existing_frames[ref]
        finally:
            self._release_lock()


    def _AddDbFrame(self, db_frame):
        self._acquire_lock()
        try:
            #create the db frame with a callback to remove it from the dict when it's garbage-collected
            #(could be a set, but that's not available on all versions we want to target).
            r = weakref.ref(db_frame, self._OnDbFrameCollected)
            self.pydev_existing_frames[r] = r
        finally:
            self._release_lock()


    def create_db_frame(self, args):
        #the frame must be cached as a weak-ref (we return the actual db frame -- which will be kept
        #alive until its trace_dispatch method is not referenced anymore).
        #that's a large workaround because:
        #1. we can't have weak-references to python frame object
        #2. only from 2.5 onwards we have _current_frames support from the interpreter
        db_frame = PyDBFrame(args)
        db_frame.frame = args[-1]
        self._AddDbFrame(db_frame)
        return db_frame


    def iter_frames(self, t):
        #We cannot use yield (because of the lock)
        self._acquire_lock()
        try:
            ret = []

            for weak_db_frame in self._iter_frames():
                try:
                    ret.append(weak_db_frame().frame)
                except AttributeError:
                    pass  # ok, garbage-collected already
            return ret
        finally:
            self._release_lock()

    def __str__(self):
        return 'State:%s Stop:%s Cmd: %s Kill:%s Frames:%s' % (
            self.pydev_state, self.pydev_step_stop, self.pydev_step_cmd, self.pydev_notify_kill, len(self.iter_frames(None)))

#=======================================================================================================================
# NOW, WE HAVE TO DEFINE WHICH THREAD INFO TO USE
# (whether we have to keep references to the frames or not)
# from version 2.5 onwards, we can use sys._current_frames to get a dict with the threads
# and frames, but to support other versions, we can't rely on that.
#=======================================================================================================================
if not hasattr(sys, '_current_frames'):
    try:
        import threadframe  #@UnresolvedImport
        sys._current_frames = threadframe.dict
        assert sys._current_frames is threadframe.dict  #Just check if it was correctly set
    except:
        #If all fails, let's use the support without frames
        PyDBAdditionalThreadInfo = PyDBAdditionalThreadInfoWithoutCurrentFramesSupport

# ENDIF
