import sys
from pydevd_constants import * #@UnusedWildImport
if USE_LIB_COPY:
    import _pydev_threading as threading
else:
    import threading
from pydevd_frame import PyDBFrame
import weakref

#=======================================================================================================================
# AbstractPyDBAdditionalThreadInfo
#=======================================================================================================================
class AbstractPyDBAdditionalThreadInfo:
    def __init__(self):
        self.pydev_state = STATE_RUN 
        self.pydev_step_stop = None
        self.pydev_step_cmd = None
        self.pydev_notify_kill = False
        self.pydev_force_stop_at_exception = None
        self.pydev_smart_step_stop = None
        self.pydev_django_resolve_frame = None
        self.is_tracing = False

        
    def IterFrames(self):
        raise NotImplementedError()
    
    def CreateDbFrame(self, args):
        #args = mainDebugger, filename, base, additionalInfo, t, frame
        raise NotImplementedError()
    
    def __str__(self):
        return 'State:%s Stop:%s Cmd: %s Kill:%s' % (self.pydev_state, self.pydev_step_stop, self.pydev_step_cmd, self.pydev_notify_kill)

    
#=======================================================================================================================
# PyDBAdditionalThreadInfoWithCurrentFramesSupport
#=======================================================================================================================
class PyDBAdditionalThreadInfoWithCurrentFramesSupport(AbstractPyDBAdditionalThreadInfo):
    
    def IterFrames(self):
        #sys._current_frames(): dictionary with thread id -> topmost frame
        return sys._current_frames().values() #return a copy... don't know if it's changed if we did get an iterator

    #just create the db frame directly
    CreateDbFrame = PyDBFrame
    
#=======================================================================================================================
# PyDBAdditionalThreadInfoWithoutCurrentFramesSupport
#=======================================================================================================================
class PyDBAdditionalThreadInfoWithoutCurrentFramesSupport(AbstractPyDBAdditionalThreadInfo):
    
    def __init__(self):
        AbstractPyDBAdditionalThreadInfo.__init__(self)
        #That's where the last frame entered is kept. That's needed so that we're able to 
        #trace contexts that were previously untraced and are currently active. So, the bad thing
        #is that the frame may be kept alive longer than it would if we go up on the frame stack,
        #and is only disposed when some other frame is removed.
        #A better way would be if we could get the topmost frame for each thread, but that's 
        #not possible (until python 2.5 -- which is the PyDBAdditionalThreadInfoWithCurrentFramesSupport version)
        #Or if the user compiled threadframe (from http://www.majid.info/mylos/stories/2004/06/10/threadframe.html)
        
        #NOT RLock!! (could deadlock if it was)
        self.lock = threading.Lock()
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
    
        
    def CreateDbFrame(self, args):
        #the frame must be cached as a weak-ref (we return the actual db frame -- which will be kept
        #alive until its trace_dispatch method is not referenced anymore).
        #that's a large workaround because:
        #1. we can't have weak-references to python frame object
        #2. only from 2.5 onwards we have _current_frames support from the interpreter
        db_frame = PyDBFrame(args)
        db_frame.frame = args[-1]
        self._AddDbFrame(db_frame)
        return db_frame
    
    
    def IterFrames(self):
        #We cannot use yield (because of the lock)
        self._acquire_lock()
        try:
            ret = []
            
            for weak_db_frame in self._iter_frames():
                try:
                    ret.append(weak_db_frame().frame)
                except AttributeError:
                    pass #ok, garbage-collected already
            return ret
        finally:
            self._release_lock()

    def __str__(self):
        return 'State:%s Stop:%s Cmd: %s Kill:%s Frames:%s' % (self.pydev_state, self.pydev_step_stop, self.pydev_step_cmd, self.pydev_notify_kill, len(self.IterFrames()))

#=======================================================================================================================
# NOW, WE HAVE TO DEFINE WHICH THREAD INFO TO USE
# (whether we have to keep references to the frames or not)
# from version 2.5 onwards, we can use sys._current_frames to get a dict with the threads
# and frames, but to support other versions, we can't rely on that.
#=======================================================================================================================
if hasattr(sys, '_current_frames'):
    PyDBAdditionalThreadInfo = PyDBAdditionalThreadInfoWithCurrentFramesSupport
else:
    try:
        import threadframe
        sys._current_frames = threadframe.dict
        assert sys._current_frames is threadframe.dict #Just check if it was correctly set
        PyDBAdditionalThreadInfo = PyDBAdditionalThreadInfoWithCurrentFramesSupport
    except:
        #If all fails, let's use the support without frames
        PyDBAdditionalThreadInfo = PyDBAdditionalThreadInfoWithoutCurrentFramesSupport

        sys.stderr.write("pydev debugger: warning: sys._current_frames is not supported in Python 2.4, it is recommended to install threadframe module\n")
        sys.stderr.write("pydev debugger: warning: See http://majid.info/blog/threadframe-multithreaded-stack-frame-extraction-for-python/\n")
