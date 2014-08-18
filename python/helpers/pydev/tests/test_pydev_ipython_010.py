#TODO: This test no longer works (check if it should be fixed or removed altogether).

#import unittest
#import sys
#import os
##make it as if we were executing from the directory above this one
#sys.argv[0] = os.path.dirname(sys.argv[0])
##twice the dirname to get the previous level from this file.
#sys.path.insert(1, os.path.join(os.path.dirname(sys.argv[0])))
#
#from pydev_localhost import get_localhost
#
#
#IS_JYTHON = sys.platform.find('java') != -1
#
##=======================================================================================================================
## TestCase
##=======================================================================================================================
#class TestCase(unittest.TestCase):
#
#    def setUp(self):
#        unittest.TestCase.setUp(self)
#
#    def tearDown(self):
#        unittest.TestCase.tearDown(self)
#
#    def testIPython(self):
#        try:
#            from pydev_ipython_console import PyDevFrontEnd
#        except:
#            if IS_JYTHON:
#                return
#        front_end = PyDevFrontEnd(get_localhost(), 0)
#
#        front_end.input_buffer = 'if True:'
#        self.assert_(not front_end._on_enter())
#
#        front_end.input_buffer = 'if True:\n' + \
#            front_end.continuation_prompt() + '    a = 10\n'
#        self.assert_(not front_end._on_enter())
#
#
#        front_end.input_buffer = 'if True:\n' + \
#            front_end.continuation_prompt() + '    a = 10\n\n'
#        self.assert_(front_end._on_enter())
#
#
##        front_end.input_buffer = '  print a'
##        self.assert_(not front_end._on_enter())
##        front_end.input_buffer = ''
##        self.assert_(front_end._on_enter())
#
#
##        front_end.input_buffer = 'a.'
##        front_end.complete_current_input()
##        front_end.input_buffer = 'if True:'
##        front_end._on_enter()
#        front_end.input_buffer = 'a = 30'
#        front_end._on_enter()
#        front_end.input_buffer = 'print a'
#        front_end._on_enter()
#        front_end.input_buffer = 'a?'
#        front_end._on_enter()
#        print front_end.complete('%')
#        print front_end.complete('%e')
#        print front_end.complete('cd c:/t')
#        print front_end.complete('cd c:/temp/')
##        front_end.input_buffer = 'print raw_input("press enter\\n")'
##        front_end._on_enter()
##
#
##=======================================================================================================================
## main
##=======================================================================================================================
#if __name__ == '__main__':
#    if sys.platform.find('java') == -1:
#        #IPython not available for Jython
#        unittest.main()
#    else:
#        print('not supported on Jython')
