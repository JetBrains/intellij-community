import os.path
import sys
import weakref
import threading
import time

IS_JYTHON = sys.platform.find('java') != -1

try:
    this_file_name = __file__
except NameError:
    # stupid jython. plain old __file__ isnt working for some reason
    import test_runfiles  #@UnresolvedImport - importing the module itself
    this_file_name = test_runfiles.__file__


desired_runfiles_path = os.path.normpath(os.path.dirname(this_file_name) + "/..")
sys.path.insert(0, desired_runfiles_path)

import unittest
import pydevd_referrers
from pydev_imports import StringIO

#=======================================================================================================================
# Test
#=======================================================================================================================
class Test(unittest.TestCase):


    def testGetReferrers1(self):

        container = []
        contained = [1, 2]
        container.append(0)
        container.append(contained)

        # Ok, we have the contained in this frame and inside the given list (which on turn is in this frame too).
        # we should skip temporary references inside the get_referrer_info.
        result = pydevd_referrers.get_referrer_info(contained)
        assert 'list[1]' in result
        pydevd_referrers.print_referrers(contained, stream=StringIO())

    def testGetReferrers2(self):

        class MyClass(object):
            def __init__(self):
                pass

        contained = [1, 2]
        obj = MyClass()
        obj.contained = contained
        del contained

        # Ok, we have the contained in this frame and inside the given list (which on turn is in this frame too).
        # we should skip temporary references inside the get_referrer_info.
        result = pydevd_referrers.get_referrer_info(obj.contained)
        assert 'found_as="contained"' in result
        assert 'MyClass' in result


    def testGetReferrers3(self):

        class MyClass(object):
            def __init__(self):
                pass

        contained = [1, 2]
        obj = MyClass()
        obj.contained = contained
        del contained

        # Ok, we have the contained in this frame and inside the given list (which on turn is in this frame too).
        # we should skip temporary references inside the get_referrer_info.
        result = pydevd_referrers.get_referrer_info(obj.contained)
        assert 'found_as="contained"' in result
        assert 'MyClass' in result


    def testGetReferrers4(self):

        class MyClass(object):
            def __init__(self):
                pass

        obj = MyClass()
        obj.me = obj

        # Let's see if we detect the cycle...
        result = pydevd_referrers.get_referrer_info(obj)
        assert 'found_as="me"' in result  #Cyclic ref


    def testGetReferrers5(self):
        container = dict(a=[1])

        # Let's see if we detect the cycle...
        result = pydevd_referrers.get_referrer_info(container['a'])
        assert 'testGetReferrers5' not in result  #I.e.: NOT in the current method
        assert 'found_as="a"' in result
        assert 'dict' in result
        assert str(id(container)) in result


    def testGetReferrers6(self):
        container = dict(a=[1])

        def should_appear(obj):
            # Let's see if we detect the cycle...
            return pydevd_referrers.get_referrer_info(obj)

        result = should_appear(container['a'])
        assert 'should_appear' in result


    def testGetReferrers7(self):

        class MyThread(threading.Thread):
            def run(self):
                #Note: we do that because if we do
                self.frame = sys._getframe()

        t = MyThread()
        t.start()
        while not hasattr(t, 'frame'):
            time.sleep(0.01)

        result = pydevd_referrers.get_referrer_info(t.frame)
        assert 'MyThread' in result


if __name__ == "__main__":
    #this is so that we can run it frem the jython tests -- because we don't actually have an __main__ module
    #(so, it won't try importing the __main__ module)
    try:
        import gc
        gc.get_referrers(unittest)
    except:
        pass
    else:
        unittest.TextTestRunner().run(unittest.makeSuite(Test))
