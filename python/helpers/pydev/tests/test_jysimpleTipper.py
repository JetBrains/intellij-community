#line to run:
#java -classpath D:\bin\jython-2.1\jython.jar;D:\bin\eclipse331_1\plugins\org.junit_3.8.2.v200706111738\junit.jar;D:\bin\eclipse331_1\plugins\org.apache.ant_1.7.0.v200706080842\lib\ant.jar org.python.util.jython w:\org.python.pydev\pysrc\tests\test_jysimpleTipper.py

import unittest
import os
import sys

#this does not work (they must be in the system pythonpath)
#sys.path.insert(1, r"D:\bin\eclipse321\plugins\org.junit_3.8.1\junit.jar" ) #some late loading jar tests
#sys.path.insert(1, r"D:\bin\eclipse331_1\plugins\org.apache.ant_1.7.0.v200706080842\lib\ant.jar" ) #some late loading jar tests

IS_JYTHON = 0
if sys.platform.find('java') != -1:
    IS_JYTHON = 1
    from _pydev_jy_imports_tipper import ismethod
    from _pydev_jy_imports_tipper import isclass
    from _pydev_jy_imports_tipper import dirObj
    import _pydev_jy_imports_tipper
    from java.lang.reflect import Method #@UnresolvedImport
    from java.lang import System #@UnresolvedImport
    from java.lang import String #@UnresolvedImport
    from java.lang.System import arraycopy #@UnresolvedImport
    from java.lang.System import out #@UnresolvedImport
    import java.lang.String #@UnresolvedImport
    import org.python.core.PyDictionary #@UnresolvedImport


__DBG = 0
def dbg(s):
    if __DBG:
        sys.stdout.write('%s\n' % (s,))
        


class TestMod(unittest.TestCase):
    
    def assertArgs(self, tok, args, tips):
        for a in tips:
            if tok == a[0]:
                self.assertEquals(args, a[2])
                return
        raise AssertionError('%s not in %s', tok, tips)

    def assertIn(self, tok, tips):
        self.assertEquals(4, len(tips[0]))
        for a in tips:
            if tok == a[0]:
                return a
        s = ''
        for a in tips:
            s += str(a)
            s += '\n'
        raise AssertionError('%s not in %s' % (tok, s))

    def testImports1a(self):
        f, tip = _pydev_jy_imports_tipper.GenerateTip('java.util.HashMap')
        assert f.endswith('rt.jar')

    def testImports1c(self):
        f, tip = _pydev_jy_imports_tipper.GenerateTip('java.lang.Class')
        assert f.endswith('rt.jar')
        
    def testImports1b(self):
        try:
            f, tip = _pydev_jy_imports_tipper.GenerateTip('__builtin__.m')
            self.fail('err')
        except:
            pass

    def testImports1(self):
        f, tip = _pydev_jy_imports_tipper.GenerateTip('junit.framework.TestCase')
        assert f.endswith('junit.jar')
        ret = self.assertIn('assertEquals', tip)
#        self.assertEquals('', ret[2])
        
    def testImports2(self):
        f, tip = _pydev_jy_imports_tipper.GenerateTip('junit.framework')
        assert f.endswith('junit.jar')
        ret = self.assertIn('TestCase', tip)
        self.assertEquals('', ret[2])
        
    def testImports2a(self):
        f, tip = _pydev_jy_imports_tipper.GenerateTip('org.apache.tools.ant')
        assert f.endswith('ant.jar')
        ret = self.assertIn('Task', tip)
        self.assertEquals('', ret[2])
        
    def testImports3(self):
        f, tip = _pydev_jy_imports_tipper.GenerateTip('os')
        assert f.endswith('os.py')
        ret = self.assertIn('path', tip)
        self.assertEquals('', ret[2])
        
    def testTipOnString(self):
        f, tip = _pydev_jy_imports_tipper.GenerateTip('string')
        self.assertIn('join', tip)
        self.assertIn('uppercase', tip)
        
    def testImports(self):
        tip = _pydev_jy_imports_tipper.GenerateTip('__builtin__')[1]
        self.assertIn('tuple'          , tip)
        self.assertIn('RuntimeError'   , tip)
        self.assertIn('RuntimeWarning' , tip)

    def testImports5(self):
        f, tip = _pydev_jy_imports_tipper.GenerateTip('java.lang')
        assert f.endswith('rt.jar')
        tup = self.assertIn('String' , tip)
        self.assertEquals(str(_pydev_jy_imports_tipper.TYPE_CLASS), tup[3])
        
        tip = _pydev_jy_imports_tipper.GenerateTip('java')[1]
        tup = self.assertIn('lang' , tip)
        self.assertEquals(str(_pydev_jy_imports_tipper.TYPE_IMPORT), tup[3])
        
        tip = _pydev_jy_imports_tipper.GenerateTip('java.lang.String')[1]
        tup = self.assertIn('indexOf'          , tip)
        self.assertEquals(str(_pydev_jy_imports_tipper.TYPE_FUNCTION), tup[3])

        tip = _pydev_jy_imports_tipper.GenerateTip('java.lang.String')[1]
        tup = self.assertIn('charAt'          , tip)
        self.assertEquals(str(_pydev_jy_imports_tipper.TYPE_FUNCTION), tup[3])
        self.assertEquals('(int)', tup[2])

        tup = self.assertIn('format'          , tip)
        self.assertEquals(str(_pydev_jy_imports_tipper.TYPE_FUNCTION), tup[3])
        self.assertEquals('(string, objectArray)', tup[2])
        self.assert_(tup[1].find('[Ljava.lang.Object;') == -1)

        tup = self.assertIn('getBytes'          , tip)
        self.assertEquals(str(_pydev_jy_imports_tipper.TYPE_FUNCTION), tup[3])
        self.assert_(tup[1].find('[B') == -1)
        self.assert_(tup[1].find('byte[]') != -1)

        f, tip = _pydev_jy_imports_tipper.GenerateTip('__builtin__.str')
        assert f.endswith('jython.jar')
        self.assertIn('find'          , tip)

        f, tip = _pydev_jy_imports_tipper.GenerateTip('__builtin__.dict')
        assert f.endswith('jython.jar')
        self.assertIn('get'          , tip)


class TestSearch(unittest.TestCase):

    def testSearchOnJython(self):
        self.assertEqual('javaos.py', _pydev_jy_imports_tipper.Search('os')[0][0].split(os.sep)[-1])
        self.assertEqual(0, _pydev_jy_imports_tipper.Search('os')[0][1])
        
        self.assertEqual('javaos.py', _pydev_jy_imports_tipper.Search('os.makedirs')[0][0].split(os.sep)[-1])
        self.assertNotEqual(0, _pydev_jy_imports_tipper.Search('os.makedirs')[0][1])
        
        #print _pydev_jy_imports_tipper.Search('os.makedirs')

class TestCompl(unittest.TestCase):

    def setUp(self):
        unittest.TestCase.setUp(self)

    def tearDown(self):
        unittest.TestCase.tearDown(self)

    def testGettingInfoOnJython(self):
        
        dbg('\n\n--------------------------- java')
        assert not ismethod(java)[0]
        assert not isclass(java)
        assert _pydev_jy_imports_tipper.ismodule(java)
            
        dbg('\n\n--------------------------- java.lang')
        assert not ismethod(java.lang)[0]
        assert not isclass(java.lang)
        assert _pydev_jy_imports_tipper.ismodule(java.lang)
            
        dbg('\n\n--------------------------- Method')
        assert not ismethod(Method)[0]
        assert isclass(Method)
            
        dbg('\n\n--------------------------- System')
        assert not ismethod(System)[0]
        assert isclass(System)
            
        dbg('\n\n--------------------------- String')
        assert not ismethod(System)[0]
        assert isclass(String)
        assert len(dirObj(String)) > 10
            
        dbg('\n\n--------------------------- arraycopy')
        isMet = ismethod(arraycopy)
        assert isMet[0]
        assert isMet[1][0].basicAsStr() == "function:arraycopy args=['java.lang.Object', 'int', 'java.lang.Object', 'int', 'int'], varargs=None, kwargs=None, docs:None"
        assert not isclass(arraycopy)
            
        dbg('\n\n--------------------------- out')
        isMet = ismethod(out)
        assert not isMet[0]
        assert not isclass(out)
            
        dbg('\n\n--------------------------- out.println')
        isMet = ismethod(out.println) #@UndefinedVariable
        assert isMet[0]
        assert len(isMet[1]) == 10
        self.assertEquals(isMet[1][0].basicAsStr(), "function:println args=[], varargs=None, kwargs=None, docs:None")
        assert isMet[1][1].basicAsStr() == "function:println args=['long'], varargs=None, kwargs=None, docs:None"
        assert not isclass(out.println) #@UndefinedVariable
        
        dbg('\n\n--------------------------- str')
        isMet = ismethod(str)
        #the code below should work, but is failing on jython 22a1
        #assert isMet[0]
        #assert isMet[1][0].basicAsStr() == "function:str args=['org.python.core.PyObject'], varargs=None, kwargs=None, docs:None"
        assert not isclass(str)
        
        
        def met1():
            a = 3
            return a
        
        dbg('\n\n--------------------------- met1')
        isMet = ismethod(met1)
        assert isMet[0]
        assert isMet[1][0].basicAsStr() == "function:met1 args=[], varargs=None, kwargs=None, docs:None"
        assert not isclass(met1)
        
        def met2(arg1, arg2, *vararg, **kwarg):
            '''docmet2'''
            
            a = 1
            return a
        
        dbg('\n\n--------------------------- met2')
        isMet = ismethod(met2)
        assert isMet[0]
        assert isMet[1][0].basicAsStr() == "function:met2 args=['arg1', 'arg2'], varargs=vararg, kwargs=kwarg, docs:docmet2"
        assert not isclass(met2)
        
        
if not IS_JYTHON:
    # Disable tests if not running under Jython
    class TestMod(unittest.TestCase):
        pass
    class TestCompl(TestMod):
        pass
    class TestSearch(TestMod):
        pass


if __name__ == '__main__':
    #Only run if jython
    suite = unittest.makeSuite(TestCompl)
    suite2 = unittest.makeSuite(TestMod)
    suite3 = unittest.makeSuite(TestSearch)
    
    unittest.TextTestRunner(verbosity=1).run(suite)
    unittest.TextTestRunner(verbosity=1).run(suite2)
    unittest.TextTestRunner(verbosity=1).run(suite3)
        
