'''
    The idea is that we record the commands sent to the debugger and reproduce them from this script
    (so, this works as the client, which spawns the debugger as a separate process and communicates
    to it as if it was run from the outside)

    Note that it's a python script but it'll spawn a process to run as jython, ironpython and as python.
'''
from tests_python.debugger_unittest import get_free_port
import threading




CMD_SET_PROPERTY_TRACE, CMD_EVALUATE_CONSOLE_EXPRESSION, CMD_RUN_CUSTOM_OPERATION, CMD_ENABLE_DONT_TRACE = 133, 134, 135, 141
PYTHON_EXE = None
IRONPYTHON_EXE = None
JYTHON_JAR_LOCATION = None
JAVA_LOCATION = None


import unittest
import os
import sys
import time
from tests_python import debugger_unittest

TEST_DJANGO = False
if sys.version_info[:2] == (2, 7):
    # Only test on python 2.7 for now
    try:
        import django
        TEST_DJANGO = True
    except:
        pass

TEST_CYTHON = os.getenv('PYDEVD_USE_CYTHON', None) == 'YES'

#=======================================================================================================================
# WriterThreadCaseSetNextStatement
#======================================================================================================================
class WriterThreadCaseSetNextStatement(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case_set_next_statement.py')

    def run(self):
        self.start_socket()
        breakpoint_id = self.write_add_breakpoint(6, None)
        self.write_make_initial_run()

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)

        assert line == 6, 'Expected return to be in line 6, was: %s' % line

        self.write_evaluate_expression('%s\t%s\t%s' % (thread_id, frame_id, 'LOCAL'), 'a')
        self.wait_for_evaluation('<var name="a" type="int" value="int: 2"')
        self.write_set_next_statement(thread_id, 2, 'method')
        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)
        assert line == 2, 'Expected return to be in line 2, was: %s' % line

        self.write_step_over(thread_id)
        thread_id, frame_id, line = self.wait_for_breakpoint_hit('108', True)

        self.write_evaluate_expression('%s\t%s\t%s' % (thread_id, frame_id, 'LOCAL'), 'a')
        self.wait_for_evaluation('<var name="a" type="int" value="int: 1"')

        self.write_remove_breakpoint(breakpoint_id)
        self.write_run_thread(thread_id)

        self.finished_ok = True

#=======================================================================================================================
# WriterThreadCaseDjango
#======================================================================================================================
class WriterThreadCaseDjango(debugger_unittest.AbstractWriterThread):

    FORCE_KILL_PROCESS_WHEN_FINISHED_OK = True

    def get_command_line_args(self):
        free_port = get_free_port()
        self.django_port = free_port
        return [
            debugger_unittest._get_debugger_test_file(os.path.join('my_django_proj_17', 'manage.py')),
            'runserver',
            '--noreload',
            str(free_port),
        ]

    def write_add_breakpoint(self, line, func):
        '''
            @param line: starts at 1
        '''
        breakpoint_id = self.next_breakpoint_id()
        template_file = debugger_unittest._get_debugger_test_file(os.path.join('my_django_proj_17', 'my_app', 'templates', 'my_app', 'index.html'))
        self.write("111\t%s\t%s\t%s\t%s\t%s\t%s\tNone\tNone" % (self.next_seq(), breakpoint_id, 'django-line', template_file, line, func))
        self.log.append('write_add_django_breakpoint: %s line: %s func: %s' % (breakpoint_id, line, func))
        return breakpoint_id


    def run(self):
        self.start_socket()
        self.write_add_breakpoint(5, None)
        self.write_make_initial_run()
        django_port = self.django_port

        class T(threading.Thread):
            def run(self):
                try:
                    from urllib.request import urlopen
                except ImportError:
                    from urllib import urlopen
                stream = urlopen('http://127.0.0.1:%s/my_app' % django_port)
                self.contents = stream.read()

        t = T()
        t.start()

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)
        assert line == 5, 'Expected return to be in line 5, was: %s' % line
        self.write_get_variable(thread_id, frame_id, 'entry')
        self.wait_for_var('<var name="key" type="str"')
        self.wait_for_var('v1')

        self.write_run_thread(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)
        assert line == 5, 'Expected return to be in line 5, was: %s' % line
        self.write_get_variable(thread_id, frame_id, 'entry')
        self.wait_for_var('<var name="key" type="str"')
        self.wait_for_var('v2')

        self.write_run_thread(thread_id)

        for i in xrange(10):
            if hasattr(t, 'contents'):
                break
            time.sleep(.3)
        else:
            raise AssertionError('Django did not return contents properly!')

        contents = t.contents.replace(' ', '').replace('\r', '').replace('\n', '')
        if contents != '<ul><li>v1:v1</li><li>v2:v2</li></ul>':
            raise AssertionError('%s != <ul><li>v1:v1</li><li>v2:v2</li></ul>' % (contents,))

        self.finished_ok = True

#=======================================================================================================================
# WriterThreadCase19 - [Test Case]: Evaluate '__' attributes
#======================================================================================================================
class WriterThreadCase19(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case19.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(8, None)
        self.write_make_initial_run()

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)

        assert line == 8, 'Expected return to be in line 8, was: %s' % line

        self.write_evaluate_expression('%s\t%s\t%s' % (thread_id, frame_id, 'LOCAL'), 'a.__var')
        self.wait_for_evaluation('<var name="a.__var" type="int" value="int')
        self.write_run_thread(thread_id)


        self.finished_ok = True


#=======================================================================================================================
# WriterThreadCase18 - [Test Case]: change local variable
#======================================================================================================================
class WriterThreadCase18(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case18.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(5, 'm2')
        self.write_make_initial_run()

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)
        assert line == 5, 'Expected return to be in line 2, was: %s' % line

        self.write_change_variable(thread_id, frame_id, 'a', '40')
        self.write_run_thread(thread_id)

        self.finished_ok = True

#=======================================================================================================================
# WriterThreadCase17 - [Test Case]: dont trace
#======================================================================================================================
class WriterThreadCase17(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case17.py')

    def run(self):
        self.start_socket()
        self.write_enable_dont_trace(True)
        self.write_add_breakpoint(27, 'main')
        self.write_add_breakpoint(29, 'main')
        self.write_add_breakpoint(31, 'main')
        self.write_add_breakpoint(33, 'main')
        self.write_make_initial_run()

        for i in range(4):
            thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)

            self.write_step_in(thread_id)
            thread_id, frame_id, line = self.wait_for_breakpoint_hit('107', True)
            # Should Skip step into properties setter
            assert line == 2, 'Expected return to be in line 2, was: %s' % line
            self.write_run_thread(thread_id)


        self.finished_ok = True

#=======================================================================================================================
# WriterThreadCase17a - [Test Case]: dont trace return
#======================================================================================================================
class WriterThreadCase17a(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case17a.py')

    def run(self):
        self.start_socket()
        self.write_enable_dont_trace(True)
        self.write_add_breakpoint(2, 'm1')
        self.write_make_initial_run()

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)

        self.write_step_in(thread_id)
        thread_id, frame_id, line = self.wait_for_breakpoint_hit('107', True)
        # Should Skip step into properties setter
        assert line == 10, 'Expected return to be in line 10, was: %s' % line
        self.write_run_thread(thread_id)


        self.finished_ok = True

#=======================================================================================================================
# WriterThreadCase16 - [Test Case]: numpy.ndarray resolver
#======================================================================================================================
class WriterThreadCase16(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case16.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(9, 'main')
        self.write_make_initial_run()

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)

        # In this test we check that the three arrays of different shapes, sizes and types
        # are all resolved properly as ndarrays.

        # First pass check is that we have all three expected variables defined
        self.write_get_frame(thread_id, frame_id)
        self.wait_for_vars('<var name="smallarray" type="ndarray" value="ndarray%253A %255B  0.%252B1.j   1.%252B1.j   2.%252B1.j   3.%252B1.j   4.%252B1.j   5.%252B1.j   6.%252B1.j   7.%252B1.j%250A   8.%252B1.j   9.%252B1.j  10.%252B1.j  11.%252B1.j  12.%252B1.j  13.%252B1.j  14.%252B1.j  15.%252B1.j%250A  16.%252B1.j  17.%252B1.j  18.%252B1.j  19.%252B1.j  20.%252B1.j  21.%252B1.j  22.%252B1.j  23.%252B1.j%250A  24.%252B1.j  25.%252B1.j  26.%252B1.j  27.%252B1.j  28.%252B1.j  29.%252B1.j  30.%252B1.j  31.%252B1.j%250A  32.%252B1.j  33.%252B1.j  34.%252B1.j  35.%252B1.j  36.%252B1.j  37.%252B1.j  38.%252B1.j  39.%252B1.j%250A  40.%252B1.j  41.%252B1.j  42.%252B1.j  43.%252B1.j  44.%252B1.j  45.%252B1.j  46.%252B1.j  47.%252B1.j%250A  48.%252B1.j  49.%252B1.j  50.%252B1.j  51.%252B1.j  52.%252B1.j  53.%252B1.j  54.%252B1.j  55.%252B1.j%250A  56.%252B1.j  57.%252B1.j  58.%252B1.j  59.%252B1.j  60.%252B1.j  61.%252B1.j  62.%252B1.j  63.%252B1.j%250A  64.%252B1.j  65.%252B1.j  66.%252B1.j  67.%252B1.j  68.%252B1.j  69.%252B1.j  70.%252B1.j  71.%252B1.j%250A  72.%252B1.j  73.%252B1.j  74.%252B1.j  75.%252B1.j  76.%252B1.j  77.%252B1.j  78.%252B1.j  79.%252B1.j%250A  80.%252B1.j  81.%252B1.j  82.%252B1.j  83.%252B1.j  84.%252B1.j  85.%252B1.j  86.%252B1.j  87.%252B1.j%250A  88.%252B1.j  89.%252B1.j  90.%252B1.j  91.%252B1.j  92.%252B1.j  93.%252B1.j  94.%252B1.j  95.%252B1.j%250A  96.%252B1.j  97.%252B1.j  98.%252B1.j  99.%252B1.j%255D" isContainer="True" />')
        self.wait_for_vars('<var name="bigarray" type="ndarray" value="ndarray%253A %255B%255B    0     1     2 ...%252C  9997  9998  9999%255D%250A %255B10000 10001 10002 ...%252C 19997 19998 19999%255D%250A %255B20000 20001 20002 ...%252C 29997 29998 29999%255D%250A ...%252C %250A %255B70000 70001 70002 ...%252C 79997 79998 79999%255D%250A %255B80000 80001 80002 ...%252C 89997 89998 89999%255D%250A %255B90000 90001 90002 ...%252C 99997 99998 99999%255D%255D" isContainer="True" />')
        self.wait_for_vars('<var name="hugearray" type="ndarray" value="ndarray%253A %255B      0       1       2 ...%252C 9999997 9999998 9999999%255D" isContainer="True" />')

        # For each variable, check each of the resolved (meta data) attributes...
        self.write_get_variable(thread_id, frame_id, 'smallarray')
        self.wait_for_var('<var name="min" type="complex128"')
        self.wait_for_var('<var name="max" type="complex128"')
        self.wait_for_var('<var name="shape" type="tuple"')
        self.wait_for_var('<var name="dtype" type="dtype"')
        self.wait_for_var('<var name="size" type="int"')
        # ...and check that the internals are resolved properly
        self.write_get_variable(thread_id, frame_id, 'smallarray\t__internals__')
        self.wait_for_var('<var name="%27size%27')

        self.write_get_variable(thread_id, frame_id, 'bigarray')
        self.wait_for_var([
            '<var name="min" type="int64" value="int64%253A 0" />',
            '<var name="min" type="int64" value="int64%3A 0" />',
            '<var name="size" type="int" value="int%3A 100000" />',
        ])
        self.wait_for_var([
            '<var name="max" type="int64" value="int64%253A 99999" />',
            '<var name="max" type="int32" value="int32%253A 99999" />',
            '<var name="max" type="int64" value="int64%3A 99999"'
        ])
        self.wait_for_var('<var name="shape" type="tuple"')
        self.wait_for_var('<var name="dtype" type="dtype"')
        self.wait_for_var('<var name="size" type="int"')
        self.write_get_variable(thread_id, frame_id, 'bigarray\t__internals__')
        self.wait_for_var('<var name="%27size%27')

        # this one is different because it crosses the magic threshold where we don't calculate
        # the min/max
        self.write_get_variable(thread_id, frame_id, 'hugearray')
        self.wait_for_var([
            '<var name="min" type="str" value="str%253A ndarray too big%252C calculating min would slow down debugging" />',
            '<var name="min" type="str" value="str%3A ndarray too big%252C calculating min would slow down debugging" />',
        ])
        self.wait_for_var([
            '<var name="max" type="str" value="str%253A ndarray too big%252C calculating max would slow down debugging" />',
            '<var name="max" type="str" value="str%3A ndarray too big%252C calculating max would slow down debugging" />',
        ])
        self.wait_for_var('<var name="shape" type="tuple"')
        self.wait_for_var('<var name="dtype" type="dtype"')
        self.wait_for_var('<var name="size" type="int"')
        self.write_get_variable(thread_id, frame_id, 'hugearray\t__internals__')
        self.wait_for_var('<var name="%27size%27')

        self.write_run_thread(thread_id)
        self.finished_ok = True


#=======================================================================================================================
# WriterThreadCase15 - [Test Case]: Custom Commands
#======================================================================================================================
class WriterThreadCase15(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case15.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(22, 'main')
        self.write_make_initial_run()

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)

        # Access some variable
        self.write_custom_operation("%s\t%s\tEXPRESSION\tcarObj.color" % (thread_id, frame_id), "EXEC", "f=lambda x: 'val=%s' % x", "f")
        self.wait_for_custom_operation('val=Black')
        assert 7 == self._sequence, 'Expected 7. Had: %s' % self._sequence

        self.write_custom_operation("%s\t%s\tEXPRESSION\tcarObj.color" % (thread_id, frame_id), "EXECFILE", debugger_unittest._get_debugger_test_file('_debugger_case15_execfile.py'), "f")
        self.wait_for_custom_operation('val=Black')
        assert 9 == self._sequence, 'Expected 9. Had: %s' % self._sequence

        self.write_run_thread(thread_id)
        self.finished_ok = True



#=======================================================================================================================
# WriterThreadCase14 - [Test Case]: Interactive Debug Console
#======================================================================================================================
class WriterThreadCase14(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case14.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(22, 'main')
        self.write_make_initial_run()

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)
        assert thread_id, '%s not valid.' % thread_id
        assert frame_id, '%s not valid.' % frame_id

        # Access some variable
        self.write_debug_console_expression("%s\t%s\tEVALUATE\tcarObj.color" % (thread_id, frame_id))
        self.wait_for_var(['<more>False</more>', '%27Black%27'])
        assert 7 == self._sequence, 'Expected 9. Had: %s' % self._sequence

        # Change some variable
        self.write_debug_console_expression("%s\t%s\tEVALUATE\tcarObj.color='Red'" % (thread_id, frame_id))
        self.write_debug_console_expression("%s\t%s\tEVALUATE\tcarObj.color" % (thread_id, frame_id))
        self.wait_for_var(['<more>False</more>', '%27Red%27'])
        assert 11 == self._sequence, 'Expected 13. Had: %s' % self._sequence

        # Iterate some loop
        self.write_debug_console_expression("%s\t%s\tEVALUATE\tfor i in range(3):" % (thread_id, frame_id))
        self.wait_for_var(['<xml><more>True</more></xml>', '<xml><more>1</more></xml>'])
        self.write_debug_console_expression("%s\t%s\tEVALUATE\t    print(i)" % (thread_id, frame_id))
        self.write_debug_console_expression("%s\t%s\tEVALUATE\t" % (thread_id, frame_id))
        self.wait_for_var(
            [
                '<xml><more>False</more><output message="0"></output><output message="1"></output><output message="2"></output></xml>',
                '<xml><more>0</more><output message="0"></output><output message="1"></output><output message="2"></output></xml>'
            ]
            )
        assert 17 == self._sequence, 'Expected 19. Had: %s' % self._sequence

        self.write_run_thread(thread_id)
        self.finished_ok = True


#=======================================================================================================================
# WriterThreadCase13
#======================================================================================================================
class WriterThreadCase13(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case13.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(35, 'main')
        self.write("%s\t%s\t%s" % (CMD_SET_PROPERTY_TRACE, self.next_seq(), "true;false;false;true"))
        self.write_make_initial_run()
        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)

        self.write_get_frame(thread_id, frame_id)

        self.write_step_in(thread_id)
        thread_id, frame_id, line = self.wait_for_breakpoint_hit('107', True)
        # Should go inside setter method
        assert line == 25, 'Expected return to be in line 25, was: %s' % line

        self.write_step_in(thread_id)
        thread_id, frame_id, line = self.wait_for_breakpoint_hit('107', True)

        self.write_step_in(thread_id)
        thread_id, frame_id, line = self.wait_for_breakpoint_hit('107', True)
        # Should go inside getter method
        assert line == 21, 'Expected return to be in line 21, was: %s' % line

        self.write_step_in(thread_id)
        thread_id, frame_id, line = self.wait_for_breakpoint_hit('107', True)

        # Disable property tracing
        self.write("%s\t%s\t%s" % (CMD_SET_PROPERTY_TRACE, self.next_seq(), "true;true;true;true"))
        self.write_step_in(thread_id)
        thread_id, frame_id, line = self.wait_for_breakpoint_hit('107', True)
        # Should Skip step into properties setter
        assert line == 39, 'Expected return to be in line 39, was: %s' % line

        # Enable property tracing
        self.write("%s\t%s\t%s" % (CMD_SET_PROPERTY_TRACE, self.next_seq(), "true;false;false;true"))
        self.write_step_in(thread_id)
        thread_id, frame_id, line = self.wait_for_breakpoint_hit('107', True)
        # Should go inside getter method
        assert line == 8, 'Expected return to be in line 8, was: %s' % line

        self.write_run_thread(thread_id)

        self.finished_ok = True

#=======================================================================================================================
# WriterThreadCase12
#======================================================================================================================
class WriterThreadCase12(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case10.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(2, '')  # Should not be hit: setting empty function (not None) should only hit global.
        self.write_add_breakpoint(6, 'Method1a')
        self.write_add_breakpoint(11, 'Method2')
        self.write_make_initial_run()

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)

        assert line == 11, 'Expected return to be in line 11, was: %s' % line

        self.write_step_return(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)  # not a return (it stopped in the other breakpoint)

        assert line == 6, 'Expected return to be in line 6, was: %s' % line

        self.write_run_thread(thread_id)

        assert 13 == self._sequence, 'Expected 13. Had: %s' % self._sequence

        self.finished_ok = True



#=======================================================================================================================
# WriterThreadCase11
#======================================================================================================================
class WriterThreadCase11(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case10.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(2, 'Method1')
        self.write_make_initial_run()

        thread_id, frame_id = self.wait_for_breakpoint_hit('111')

        self.write_step_over(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('108', True)

        assert line == 3, 'Expected return to be in line 3, was: %s' % line

        self.write_step_over(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('108', True)

        assert line == 11, 'Expected return to be in line 11, was: %s' % line

        self.write_step_over(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('108', True)

        assert line == 12, 'Expected return to be in line 12, was: %s' % line

        self.write_run_thread(thread_id)

        assert 13 == self._sequence, 'Expected 13. Had: %s' % self._sequence

        self.finished_ok = True



#=======================================================================================================================
# WriterThreadCase10
#======================================================================================================================
class WriterThreadCase10(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case10.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(2, 'None')  # None or Method should make hit.
        self.write_make_initial_run()

        thread_id, frame_id = self.wait_for_breakpoint_hit('111')

        self.write_step_return(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('109', True)

        assert line == 11, 'Expected return to be in line 11, was: %s' % line

        self.write_step_over(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('108', True)

        assert line == 12, 'Expected return to be in line 12, was: %s' % line

        self.write_run_thread(thread_id)

        assert 11 == self._sequence, 'Expected 11. Had: %s' % self._sequence

        self.finished_ok = True



#=======================================================================================================================
# WriterThreadCase9
#======================================================================================================================
class WriterThreadCase9(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case89.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(10, 'Method3')
        self.write_make_initial_run()

        thread_id, frame_id = self.wait_for_breakpoint_hit('111')

        self.write_step_over(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('108', True)

        assert line == 11, 'Expected return to be in line 11, was: %s' % line

        self.write_step_over(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('108', True)

        assert line == 12, 'Expected return to be in line 12, was: %s' % line

        self.write_run_thread(thread_id)

        assert 11 == self._sequence, 'Expected 11. Had: %s' % self._sequence

        self.finished_ok = True


#=======================================================================================================================
# WriterThreadCase8
#======================================================================================================================
class WriterThreadCase8(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case89.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(10, 'Method3')
        self.write_make_initial_run()

        thread_id, frame_id = self.wait_for_breakpoint_hit('111')

        self.write_step_return(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('109', True)

        assert line == 15, 'Expected return to be in line 15, was: %s' % line

        self.write_run_thread(thread_id)

        assert 9 == self._sequence, 'Expected 9. Had: %s' % self._sequence

        self.finished_ok = True




#=======================================================================================================================
# WriterThreadCase7
#======================================================================================================================
class WriterThreadCase7(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case7.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(2, 'Call')
        self.write_make_initial_run()

        thread_id, frame_id = self.wait_for_breakpoint_hit('111')

        self.write_get_frame(thread_id, frame_id)

        self.wait_for_vars('<xml></xml>')  # no vars at this point

        self.write_step_over(thread_id)

        self.write_get_frame(thread_id, frame_id)

        self.wait_for_vars('<xml><var name="variable_for_test_1" type="int" value="int%253A 10" />%0A</xml>')

        self.write_step_over(thread_id)

        self.write_get_frame(thread_id, frame_id)

        self.wait_for_vars('<xml><var name="variable_for_test_1" type="int" value="int%253A 10" />%0A<var name="variable_for_test_2" type="int" value="int%253A 20" />%0A</xml>')

        self.write_run_thread(thread_id)

        assert 17 == self._sequence, 'Expected 17. Had: %s' % self._sequence

        self.finished_ok = True



#=======================================================================================================================
# WriterThreadCase6
#=======================================================================================================================
class WriterThreadCase6(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case56.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(2, 'Call2')
        self.write_make_initial_run()

        thread_id, frame_id = self.wait_for_breakpoint_hit()

        self.write_get_frame(thread_id, frame_id)

        self.write_step_return(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('109', True)

        assert line == 8, 'Expecting it to go to line 8. Went to: %s' % line

        self.write_step_in(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('107', True)

        # goes to line 4 in jython (function declaration line)
        assert line in (4, 5), 'Expecting it to go to line 4 or 5. Went to: %s' % line

        self.write_run_thread(thread_id)

        assert 13 == self._sequence, 'Expected 15. Had: %s' % self._sequence

        self.finished_ok = True

#=======================================================================================================================
# WriterThreadCase5
#=======================================================================================================================
class WriterThreadCase5(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case56.py')

    def run(self):
        self.start_socket()
        breakpoint_id = self.write_add_breakpoint(2, 'Call2')
        self.write_make_initial_run()

        thread_id, frame_id = self.wait_for_breakpoint_hit()

        self.write_get_frame(thread_id, frame_id)

        self.write_remove_breakpoint(breakpoint_id)

        self.write_step_return(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('109', True)

        assert line == 8, 'Expecting it to go to line 8. Went to: %s' % line

        self.write_step_in(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('107', True)

        # goes to line 4 in jython (function declaration line)
        assert line in (4, 5), 'Expecting it to go to line 4 or 5. Went to: %s' % line

        self.write_run_thread(thread_id)

        assert 15 == self._sequence, 'Expected 15. Had: %s' % self._sequence

        self.finished_ok = True


#=======================================================================================================================
# WriterThreadCase4
#=======================================================================================================================
class WriterThreadCase4(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case4.py')

    def run(self):
        self.start_socket()
        self.write_make_initial_run()

        thread_id = self.wait_for_new_thread()

        self.write_suspend_thread(thread_id)

        time.sleep(4)  # wait for time enough for the test to finish if it wasn't suspended

        self.write_run_thread(thread_id)

        self.finished_ok = True


#=======================================================================================================================
# WriterThreadCase3
#=======================================================================================================================
class WriterThreadCase3(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case3.py')

    def run(self):
        self.start_socket()
        self.write_make_initial_run()
        time.sleep(.5)
        breakpoint_id = self.write_add_breakpoint(4, '')
        self.write_add_breakpoint(5, 'FuncNotAvailable')  # Check that it doesn't get hit in the global when a function is available

        thread_id, frame_id = self.wait_for_breakpoint_hit()

        self.write_get_frame(thread_id, frame_id)

        self.write_run_thread(thread_id)

        thread_id, frame_id = self.wait_for_breakpoint_hit()

        self.write_get_frame(thread_id, frame_id)

        self.write_remove_breakpoint(breakpoint_id)

        self.write_run_thread(thread_id)

        assert 17 == self._sequence, 'Expected 17. Had: %s' % self._sequence

        self.finished_ok = True

#=======================================================================================================================
# WriterThreadCase2
#=======================================================================================================================
class WriterThreadCase2(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case2.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(3, 'Call4')  # seq = 3
        self.write_make_initial_run()

        thread_id, frame_id = self.wait_for_breakpoint_hit()

        self.write_get_frame(thread_id, frame_id)

        self.write_add_breakpoint(14, 'Call2')

        self.write_run_thread(thread_id)

        thread_id, frame_id = self.wait_for_breakpoint_hit()

        self.write_get_frame(thread_id, frame_id)

        self.write_run_thread(thread_id)

        self.log.append('Checking sequence. Found: %s' % (self._sequence))
        assert 15 == self._sequence, 'Expected 15. Had: %s' % self._sequence

        self.log.append('Marking finished ok.')
        self.finished_ok = True

#=======================================================================================================================
# WriterThreadCaseQThread1
#=======================================================================================================================
class WriterThreadCaseQThread1(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case_qthread1.py')

    def run(self):
        self.start_socket()
        breakpoint_id = self.write_add_breakpoint(16, 'run')
        self.write_make_initial_run()

        thread_id, frame_id = self.wait_for_breakpoint_hit()

        self.write_remove_breakpoint(breakpoint_id)
        self.write_run_thread(thread_id)

        self.log.append('Checking sequence. Found: %s' % (self._sequence))
        assert 9 == self._sequence, 'Expected 9. Had: %s' % self._sequence

        self.log.append('Marking finished ok.')
        self.finished_ok = True

#=======================================================================================================================
# WriterThreadCaseQThread2
#=======================================================================================================================
class WriterThreadCaseQThread2(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case_qthread2.py')

    def run(self):
        self.start_socket()
        breakpoint_id = self.write_add_breakpoint(21, 'long_running')
        self.write_make_initial_run()

        thread_id, frame_id = self.wait_for_breakpoint_hit()

        self.write_remove_breakpoint(breakpoint_id)
        self.write_run_thread(thread_id)

        self.log.append('Checking sequence. Found: %s' % (self._sequence))
        assert 9 == self._sequence, 'Expected 9. Had: %s' % self._sequence

        self.log.append('Marking finished ok.')
        self.finished_ok = True

#=======================================================================================================================
# WriterThreadCaseQThread3
#=======================================================================================================================
class WriterThreadCaseQThread3(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case_qthread3.py')

    def run(self):
        self.start_socket()
        breakpoint_id = self.write_add_breakpoint(19, 'run')
        self.write_make_initial_run()

        thread_id, frame_id = self.wait_for_breakpoint_hit()

        self.write_remove_breakpoint(breakpoint_id)
        self.write_run_thread(thread_id)

        self.log.append('Checking sequence. Found: %s' % (self._sequence))
        assert 9 == self._sequence, 'Expected 9. Had: %s' % self._sequence

        self.log.append('Marking finished ok.')
        self.finished_ok = True

#=======================================================================================================================
# WriterThreadCase1
#=======================================================================================================================
class WriterThreadCase1(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case1.py')

    def run(self):
        self.start_socket()

        self.log.append('writing add breakpoint')
        self.write_add_breakpoint(6, 'set_up')

        self.log.append('making initial run')
        self.write_make_initial_run()

        self.log.append('waiting for breakpoint hit')
        thread_id, frame_id = self.wait_for_breakpoint_hit()

        self.log.append('get frame')
        self.write_get_frame(thread_id, frame_id)

        self.log.append('step over')
        self.write_step_over(thread_id)

        self.log.append('get frame')
        self.write_get_frame(thread_id, frame_id)

        self.log.append('run thread')
        self.write_run_thread(thread_id)

        self.log.append('asserting')
        try:
            assert 13 == self._sequence, 'Expected 13. Had: %s' % self._sequence
        except:
            self.log.append('assert failed!')
            raise
        self.log.append('asserted')

        self.finished_ok = True

#=======================================================================================================================
# DebuggerBase
#=======================================================================================================================
class DebuggerBase(debugger_unittest.DebuggerRunner):

    def test_case_1(self):
        self.check_case(WriterThreadCase1)

    def test_case_2(self):
        self.check_case(WriterThreadCase2)

    def test_case_3(self):
        self.check_case(WriterThreadCase3)

    def test_case_4(self):
        self.check_case(WriterThreadCase4)

    def test_case_5(self):
        self.check_case(WriterThreadCase5)

    def test_case_6(self):
        self.check_case(WriterThreadCase6)

    def test_case_7(self):
        self.check_case(WriterThreadCase7)

    def test_case_8(self):
        self.check_case(WriterThreadCase8)

    def test_case_9(self):
        self.check_case(WriterThreadCase9)

    def test_case_10(self):
        self.check_case(WriterThreadCase10)

    def test_case_11(self):
        self.check_case(WriterThreadCase11)

    def test_case_12(self):
        self.check_case(WriterThreadCase12)

    def test_case_13(self):
        self.check_case(WriterThreadCase13)

    def test_case_14(self):
        self.check_case(WriterThreadCase14)

    def test_case_15(self):
        self.check_case(WriterThreadCase15)

    def test_case_16(self):
        self.check_case(WriterThreadCase16)

    def test_case_17(self):
        self.check_case(WriterThreadCase17)

    def test_case_17a(self):
        self.check_case(WriterThreadCase17a)

    def test_case_18(self):
        self.check_case(WriterThreadCase18)

    def test_case_19(self):
        self.check_case(WriterThreadCase19)

    if TEST_DJANGO:
        def test_case_django(self):
            self.check_case(WriterThreadCaseDjango)

    if TEST_CYTHON:
        def test_cython(self):
            from _pydevd_bundle import pydevd_cython
            assert pydevd_cython.trace_dispatch is not None

    def _has_qt(self):
        try:
            from PySide import QtCore  # @UnresolvedImport
            return True
        except:
            try:
                from PyQt4 import QtCore
                return True
            except:
                pass
        return False

    def test_case_qthread1(self):
        if self._has_qt():
            self.check_case(WriterThreadCaseQThread1)

    def test_case_qthread2(self):
        if self._has_qt():
            self.check_case(WriterThreadCaseQThread2)

    def test_case_qthread3(self):
        if self._has_qt():
            self.check_case(WriterThreadCaseQThread3)


class TestPython(unittest.TestCase, DebuggerBase):
    def get_command_line(self):
        return [PYTHON_EXE, '-u']

    def test_case_set_next_statement(self):
        # Set next only for Python.
        self.check_case(WriterThreadCaseSetNextStatement)

class TestJython(unittest.TestCase, DebuggerBase):
    def get_command_line(self):
        return [
                JAVA_LOCATION,
                '-classpath',
                JYTHON_JAR_LOCATION,
                'org.python.util.jython'
            ]

    # This case requires decorators to work (which are not present on Jython 2.1), so, this test is just removed from the jython run.
    def test_case_13(self):
        self.skipTest("Unsupported Decorators")

    # This case requires decorators to work (which are not present on Jython 2.1), so, this test is just removed from the jython run.
    def test_case_17(self):
        self.skipTest("Unsupported Decorators")

    def test_case_18(self):
        self.skipTest("Unsupported assign to local")

    def test_case_16(self):
        self.skipTest("Unsupported numpy")

class TestIronPython(unittest.TestCase, DebuggerBase):
    def get_command_line(self):
        return [
                IRONPYTHON_EXE,
                '-X:Frames'
            ]

    def test_case_3(self):
        self.skipTest("Timing issues") # This test fails once in a while due to timing issues on IronPython, so, skipping it.

    def test_case_7(self):
        # This test checks that we start without variables and at each step a new var is created, but on ironpython,
        # the variables exist all at once (with None values), so, we can't test it properly.
        self.skipTest("Different behavior on IronPython")

    def test_case_13(self):
        self.skipTest("Unsupported Decorators") # Not sure why it doesn't work on IronPython, but it's not so common, so, leave it be.

    def test_case_16(self):
        self.skipTest("Unsupported numpy")

    def test_case_18(self):
        self.skipTest("Unsupported assign to local")


def get_location_from_line(line):
    loc = line.split('=')[1].strip()
    if loc.endswith(';'):
        loc = loc[:-1]
    if loc.endswith('"'):
        loc = loc[:-1]
    if loc.startswith('"'):
        loc = loc[1:]
    return loc


def split_line(line):
    if '=' not in line:
        return None, None
    var = line.split('=')[0].strip()
    return var, get_location_from_line(line)




import platform
sysname = platform.system().lower()
test_dependent = os.path.join('../../../', 'org.python.pydev.core', 'tests', 'org', 'python', 'pydev', 'core', 'TestDependent.' + sysname + '.properties')

if os.path.exists(test_dependent):
    f = open(test_dependent)
    try:
        for line in f.readlines():
            var, loc = split_line(line)
            if 'PYTHON_EXE' == var:
                PYTHON_EXE = loc

            if 'IRONPYTHON_EXE' == var:
                IRONPYTHON_EXE = loc

            if 'JYTHON_JAR_LOCATION' == var:
                JYTHON_JAR_LOCATION = loc

            if 'JAVA_LOCATION' == var:
                JAVA_LOCATION = loc
    finally:
        f.close()
else:
    pass

if IRONPYTHON_EXE is None:
    sys.stderr.write('Warning: not running IronPython tests.\n')
    class TestIronPython(unittest.TestCase):
        pass

if JAVA_LOCATION is None:
    sys.stderr.write('Warning: not running Jython tests.\n')
    class TestJython(unittest.TestCase):
        pass

# if PYTHON_EXE is None:
PYTHON_EXE = sys.executable


if __name__ == '__main__':
    if False:
        assert PYTHON_EXE, 'PYTHON_EXE not found in %s' % (test_dependent,)
        assert IRONPYTHON_EXE, 'IRONPYTHON_EXE not found in %s' % (test_dependent,)
        assert JYTHON_JAR_LOCATION, 'JYTHON_JAR_LOCATION not found in %s' % (test_dependent,)
        assert JAVA_LOCATION, 'JAVA_LOCATION not found in %s' % (test_dependent,)
        assert os.path.exists(PYTHON_EXE), 'The location: %s is not valid' % (PYTHON_EXE,)
        assert os.path.exists(IRONPYTHON_EXE), 'The location: %s is not valid' % (IRONPYTHON_EXE,)
        assert os.path.exists(JYTHON_JAR_LOCATION), 'The location: %s is not valid' % (JYTHON_JAR_LOCATION,)
        assert os.path.exists(JAVA_LOCATION), 'The location: %s is not valid' % (JAVA_LOCATION,)

    if True:
        #try:
        #    os.remove(r'X:\pydev\plugins\org.python.pydev\pysrc\pydevd.pyc')
        #except:
        #    pass
        suite = unittest.TestSuite()

#         suite.addTests(unittest.makeSuite(TestJython)) # Note: Jython should be 2.2.1
#
#         suite.addTests(unittest.makeSuite(TestIronPython))
#
        suite.addTests(unittest.makeSuite(TestPython))




#         suite.addTest(TestIronPython('test_case_18'))
#         suite.addTest(TestIronPython('test_case_17'))
#         suite.addTest(TestIronPython('test_case_3'))
#         suite.addTest(TestIronPython('test_case_7'))
#
#         suite.addTest(TestPython('test_case_10'))
#         suite.addTest(TestPython('test_case_django'))
#         suite.addTest(TestPython('test_case_qthread1'))
#         suite.addTest(TestPython('test_case_qthread2'))
#         suite.addTest(TestPython('test_case_qthread3'))

#         suite.addTest(TestPython('test_case_17a'))


#         suite.addTest(TestJython('test_case_1'))
#         suite.addTest(TestPython('test_case_2'))
#         unittest.TextTestRunner(verbosity=3).run(suite)
    #     suite.addTest(TestPython('test_case_17'))
    #     suite.addTest(TestPython('test_case_18'))
    #     suite.addTest(TestPython('test_case_19'))

        unittest.TextTestRunner(verbosity=3).run(suite)
