from pydevd_constants import *  #@UnusedWildImport
from pydevd_file_utils import GetFilenameAndBase
from _pydev_imps import _pydev_thread
threadingCurrentThread = threading.currentThread

DEBUG = False

#=======================================================================================================================
# CustomFramesContainer
#=======================================================================================================================
class CustomFramesContainer:
    pass

    
def CustomFramesContainerInit(): #Note: no staticmethod on jython 2.1 (so, use free-function)
    
    CustomFramesContainer.custom_frames_lock = _pydev_thread.allocate_lock()
    
    # custom_frames can only be accessed if properly locked with custom_frames_lock! 
    # Key is a string identifying the frame (as well as the thread it belongs to). 
    # Value is a CustomFrame.
    #
    CustomFramesContainer.custom_frames = {}
    
    # Only to be used in this module
    CustomFramesContainer._next_frame_id = 0
    
    # This is the event we must set to release an internal process events. It's later set by the actual debugger
    # when we do create the debugger.
    CustomFramesContainer._py_db_command_thread_event = Null()
    
#Initialize it the first time (it may be reinitialized later on when dealing with a fork).
CustomFramesContainerInit()
    
    
#=======================================================================================================================
# CustomFrame
#=======================================================================================================================
class CustomFrame:
    
    def __init__(self, name, frame, thread_id):
        # 0 = string with the representation of that frame
        self.name = name
        
        # 1 = the frame to show
        self.frame = frame
        
        # 2 = an integer identifying the last time the frame was changed.
        self.mod_time = 0
        
        # 3 = the thread id of the given frame
        self.thread_id = thread_id


def addCustomFrame(frame, name, thread_id):
    CustomFramesContainer.custom_frames_lock.acquire()
    try:
        curr_thread_id = GetThreadId(threadingCurrentThread())
        next_id = CustomFramesContainer._next_frame_id = CustomFramesContainer._next_frame_id + 1
        
        # Note: the frame id kept contains an id and thread information on the thread where the frame was added
        # so that later on we can check if the frame is from the current thread by doing frame_id.endswith('|'+thread_id).
        frame_id = '__frame__:%s|%s' % (next_id, curr_thread_id)
        if DEBUG:
            sys.stderr.write('addCustomFrame: %s (%s) %s %s\n' % (
                frame_id, GetFilenameAndBase(frame)[1], frame.f_lineno, frame.f_code.co_name))

        CustomFramesContainer.custom_frames[frame_id] = CustomFrame(name, frame, thread_id)
        CustomFramesContainer._py_db_command_thread_event.set()
        return frame_id
    finally:
        CustomFramesContainer.custom_frames_lock.release()


def updateCustomFrame(frame_id, frame, thread_id, name=None):
    CustomFramesContainer.custom_frames_lock.acquire()
    try:
        if DEBUG:
            sys.stderr.write('updateCustomFrame: %s\n' % frame_id)
        try:
            old = CustomFramesContainer.custom_frames[frame_id]
            if name is not None:
                old.name = name
            old.mod_time += 1
            old.thread_id = thread_id
        except:
            sys.stderr.write('Unable to get frame to replace: %s\n' % (frame_id,))
            import traceback;traceback.print_exc()
            
        CustomFramesContainer._py_db_command_thread_event.set()
    finally:
        CustomFramesContainer.custom_frames_lock.release()


def getCustomFrame(thread_id, frame_id):
    '''
    :param thread_id: This should actually be the frame_id which is returned by addCustomFrame.
    :param frame_id: This is the actual id() of the frame
    '''
    
    CustomFramesContainer.custom_frames_lock.acquire()
    try:
        frame_id = int(frame_id)
        f = CustomFramesContainer.custom_frames[thread_id].frame
        while f is not None:
            if id(f) == frame_id:
                return f
            f = f.f_back
    finally:
        f = None
        CustomFramesContainer.custom_frames_lock.release()
    
    
def removeCustomFrame(frame_id):
    CustomFramesContainer.custom_frames_lock.acquire()
    try:
        if DEBUG:
            sys.stderr.write('removeCustomFrame: %s\n' % frame_id)
        DictPop(CustomFramesContainer.custom_frames, frame_id, None)
        CustomFramesContainer._py_db_command_thread_event.set()
    finally:
        CustomFramesContainer.custom_frames_lock.release()
