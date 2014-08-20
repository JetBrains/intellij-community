'''
Created on Aug 22, 2011

@author: hussain.bohra
@author: fabioz
'''

import os
import sys
import unittest

#=======================================================================================================================
# Test
#=======================================================================================================================
class Test(unittest.TestCase):
    """Test cases to validate custom property implementation in pydevd 
    """
    
    def setUp(self, nused=None):
        self.tempdir = os.path.join(os.path.dirname(os.path.dirname(__file__)))
        sys.path.insert(0, self.tempdir)
        import pydevd_traceproperty
        self.old = pydevd_traceproperty.replace_builtin_property()
    
    
    def tearDown(self, unused=None):
        import pydevd_traceproperty
        pydevd_traceproperty.replace_builtin_property(self.old)
        sys.path.remove(self.tempdir)


    def testProperty(self):
        """Test case to validate custom property
        """
        
        import pydevd_traceproperty
        class TestProperty(object):
            
            def __init__(self):
                self._get = 0
                self._set = 0
                self._del = 0
                
            def get_name(self):
                self._get += 1
                return self.__name
            
            def set_name(self, value):
                self._set += 1
                self.__name = value
                
            def del_name(self):
                self._del += 1
                del self.__name
            name = property(get_name, set_name, del_name, "name's docstring")
            self.assertEqual(name.__class__, pydevd_traceproperty.DebugProperty)
            
        testObj = TestProperty()
        self._check(testObj)
        
        
    def testProperty2(self):
        """Test case to validate custom property
        """
        
        class TestProperty(object):
            
            def __init__(self):
                self._get = 0
                self._set = 0
                self._del = 0
            
            def name(self):
                self._get += 1
                return self.__name
            name = property(name)
            
            def set_name(self, value):
                self._set += 1
                self.__name = value
            name.setter(set_name)
                
            def del_name(self):
                self._del += 1
                del self.__name
            name.deleter(del_name)

        testObj = TestProperty()
        self._check(testObj)
        
        
    def testProperty3(self):
        """Test case to validate custom property
        """
        
        class TestProperty(object):
            
            def __init__(self):
                self._name = 'foo'
            
            def name(self):
                return self._name
            name = property(name)

        testObj = TestProperty()
        self.assertRaises(AttributeError, setattr, testObj, 'name', 'bar')
        self.assertRaises(AttributeError, delattr, testObj, 'name')
        
        
    def _check(self, testObj):
        testObj.name = "Custom"
        self.assertEqual(1, testObj._set)
        
        self.assertEqual(testObj.name, "Custom")
        self.assertEqual(1, testObj._get)
        
        self.assert_(hasattr(testObj, 'name'))
        del testObj.name
        self.assertEqual(1, testObj._del)
        
        self.assert_(not hasattr(testObj, 'name'))
        testObj.name = "Custom2"
        self.assertEqual(testObj.name, "Custom2")


        
#=======================================================================================================================
# main
#=======================================================================================================================
if __name__ == '__main__':
    #this is so that we can run it from the jython tests -- because we don't actually have an __main__ module
    #(so, it won't try importing the __main__ module)
    unittest.TextTestRunner().run(unittest.makeSuite(Test))
    
