import sys
import os
import pydev_monkey
sys.path.insert(0, os.path.split(os.path.split(__file__)[0])[0])

from pydevd_constants import Null
import unittest

try:
    import thread
except:
    import _thread as thread

try:
    xrange
except:
    xrange = range
    
#=======================================================================================================================
# TestCase
#=======================================================================================================================
class TestCase(unittest.TestCase):
    '''
        Used for profiling the PyDBAdditionalThreadInfoWithoutCurrentFramesSupport version
    '''
    
    def testMetNoFramesSupport(self):
        from pydevd_additional_thread_info import PyDBAdditionalThreadInfoWithoutCurrentFramesSupport
        info = PyDBAdditionalThreadInfoWithoutCurrentFramesSupport()
        
        mainDebugger = Null()
        filename = ''
        base = ''
        additionalInfo = Null()
        t = Null()
        frame = Null()
        
        times = 10
        for i in range(times):
            info.CreateDbFrame((mainDebugger, filename, additionalInfo, t, frame))
            
        #we haven't kept any reference, so, they must have been garbage-collected already!
        self.assertEqual(0, len(info.IterFrames()))
        
        kept_frames = []
        for i in range(times):
            kept_frames.append(info.CreateDbFrame((mainDebugger, filename, additionalInfo, t, frame)))
        
        for i in range(times):
            self.assertEqual(times, len(info.IterFrames()))
            
            
    def testStartNewThread(self):
        pydev_monkey.patch_thread_modules()
        try:
            found = {}
            def function(a, b, *args, **kwargs):
                found['a'] = a
                found['b'] = b
                found['args'] = args
                found['kwargs'] = kwargs
            thread.start_new_thread(function, (1,2,3,4), {'d':1, 'e':2})
            import time
            for _i in xrange(20):
                if len(found) == 4:
                    break
                time.sleep(.1)
            else:
                raise AssertionError('Could not get to condition before 2 seconds')
            
            self.assertEqual({'a': 1, 'b': 2, 'args': (3, 4), 'kwargs': {'e': 2, 'd': 1}}, found)
        finally:
            pydev_monkey.undo_patch_thread_modules()
            
            
    def testStartNewThread2(self):
        pydev_monkey.patch_thread_modules()
        try:
            found = {}
            
            class F(object):
                start_new_thread = thread.start_new_thread
                
                def start_it(self):
                    try:
                        self.start_new_thread(self.function, (1,2,3,4), {'d':1, 'e':2})
                    except:
                        import traceback;traceback.print_exc()

                def function(self, a, b, *args, **kwargs):
                    found['a'] = a
                    found['b'] = b
                    found['args'] = args
                    found['kwargs'] = kwargs
            
            f = F()
            f.start_it()
            import time
            for _i in xrange(20):
                if len(found) == 4:
                    break
                time.sleep(.1)
            else:
                raise AssertionError('Could not get to condition before 2 seconds')
            
            self.assertEqual({'a': 1, 'b': 2, 'args': (3, 4), 'kwargs': {'e': 2, 'd': 1}}, found)
        finally:
            pydev_monkey.undo_patch_thread_modules()
        

#=======================================================================================================================
# main        
#=======================================================================================================================
if __name__ == '__main__':
    unittest.main()
