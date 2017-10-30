'''
    The idea is that we record the commands sent to the debugger and reproduce them from this script
    (so, this works as the client, which spawns the debugger as a separate process and communicates
    to it as if it was run from the outside)

    Note that it's a python script but it'll spawn a process to run as jython, ironpython and as python.
'''
import os
import platform
import sys
import threading
import time
import unittest

from tests_pydevd_python import debugger_unittest
from tests_pydevd_python.debugger_unittest import get_free_port


CMD_SET_PROPERTY_TRACE, CMD_EVALUATE_CONSOLE_EXPRESSION, CMD_RUN_CUSTOM_OPERATION, CMD_ENABLE_DONT_TRACE = 133, 134, 135, 141

IS_CPYTHON = platform.python_implementation() == 'CPython'
IS_IRONPYTHON = platform.python_implementation() == 'IronPython'
IS_JYTHON = platform.python_implementation() == 'Jython'

IS_NUMPY = True
try:
    import numpy
except ImportError:
    IS_NUMPY = False

try:
    xrange
except:
    xrange = range


TEST_DJANGO = False
if sys.version_info[:2] == (2, 7):
    # Only test on python 2.7 for now
    try:
        import django
        TEST_DJANGO = True
    except:
        pass

IS_PY2 = False
if sys.version_info[0] == 2:
    IS_PY2 = True

if IS_PY2:
    builtin_qualifier = "__builtin__"
else:
    builtin_qualifier = "builtins"

IS_PY36 = False
if sys.version_info[0] == 3 and sys.version_info[1] == 6:
    IS_PY36 = True

TEST_CYTHON = os.getenv('PYDEVD_USE_CYTHON', None) == 'YES'
TEST_JYTHON = os.getenv('TEST_JYTHON', None) == 'YES'

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
        self.wait_for_evaluation('<var name="a" type="int" qualifier="{0}" value="int: 2"'.format(builtin_qualifier))
        self.write_set_next_statement(thread_id, 2, 'method')
        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)
        assert line == 2, 'Expected return to be in line 2, was: %s' % line

        self.write_step_over(thread_id)
        thread_id, frame_id, line = self.wait_for_breakpoint_hit('108', True)

        self.write_evaluate_expression('%s\t%s\t%s' % (thread_id, frame_id, 'LOCAL'), 'a')
        self.wait_for_evaluation('<var name="a" type="int" qualifier="{0}" value="int: 1"'.format(builtin_qualifier))

        self.write_remove_breakpoint(breakpoint_id)
        self.write_run_thread(thread_id)

        self.finished_ok = True

#=======================================================================================================================
# AbstractWriterThreadCaseDjango
#======================================================================================================================
class AbstractWriterThreadCaseDjango(debugger_unittest.AbstractWriterThread):
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
    def write_add_breakpoint_django(self, line, func, template):
        '''
            @param line: starts at 1
        '''
        breakpoint_id = self.next_breakpoint_id()
        template_file = debugger_unittest._get_debugger_test_file(os.path.join('my_django_proj_17', 'my_app', 'templates', 'my_app', template))
        self.write("111\t%s\t%s\t%s\t%s\t%s\t%s\tNone\tNone" % (self.next_seq(), breakpoint_id, 'django-line', template_file, line, func))
        self.log.append('write_add_django_breakpoint: %s line: %s func: %s' % (breakpoint_id, line, func))
        return breakpoint_id

    def create_request_thread(self, uri):
        outer= self
        class T(threading.Thread):
            def run(self):
                try:
                    from urllib.request import urlopen
                except ImportError:
                    from urllib import urlopen
                for _ in xrange(10):
                    try:
                        stream = urlopen('http://127.0.0.1:%s/%s' % (outer.django_port,uri))
                        self.contents = stream.read()
                        break
                    except IOError:
                        continue
        return T()

#=======================================================================================================================
# WriterThreadCaseDjango
#======================================================================================================================
class WriterThreadCaseDjango(AbstractWriterThreadCaseDjango):

    def run(self):
        self.start_socket()
        self.write_add_breakpoint_django(5, None, 'index.html')
        self.write_make_initial_run()

        t = self.create_request_thread('my_app')
        time.sleep(2) # Give django some time to get to startup before requesting the page
        t.start()

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)
        assert line == 5, 'Expected return to be in line 5, was: %s' % line
        self.write_get_variable(thread_id, frame_id, 'entry')
        self.wait_for_vars([
            '<var name="key" type="str"',
            'v1'
        ])

        self.write_run_thread(thread_id)

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)
        assert line == 5, 'Expected return to be in line 5, was: %s' % line
        self.write_get_variable(thread_id, frame_id, 'entry')
        self.wait_for_vars([
            '<var name="key" type="str"',
            'v2'
        ])

        self.write_run_thread(thread_id)

        for _ in xrange(10):
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
# WriterThreadCaseDjango2
#======================================================================================================================
class WriterThreadCaseDjango2(AbstractWriterThreadCaseDjango):

    def run(self):
        self.start_socket()
        self.write_add_breakpoint_django(4, None, 'name.html')
        self.write_make_initial_run()

        t = self.create_request_thread('my_app/name')
        time.sleep(2) # Give django some time to get to startup before requesting the page
        t.start()

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)
        assert line == 4, 'Expected return to be in line 4, was: %s' % line

        self.write_get_frame(thread_id, frame_id)
        self.wait_for_var('<var name="form" type="NameForm" qualifier="my_app.forms" value="NameForm%253A %253Cmy_app.forms.NameForm object')
        self.write_run_thread(thread_id)
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
        self.wait_for_evaluation('<var name="a.__var" type="int" qualifier="{0}" value="int'.format(builtin_qualifier))
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
        self.wait_for_var('<xml><var name="" type="int" qualifier="{0}" value="int%253A 40" />%0A</xml>'.format(builtin_qualifier,))
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
        assert line == 2, 'Expected return to be in line 2, was: %s' % line

        self.write_step_in(thread_id)
        thread_id, frame_id, line, name = self.wait_for_breakpoint_hit('107', get_line=True, get_name=True)

        # Should Skip step into properties setter
        assert name == 'm3'
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
        self.wait_for_multiple_vars((
            '<var name="smallarray" type="ndarray" qualifier="numpy" value="ndarray%253A %255B  0.%252B1.j   1.%252B1.j   2.%252B1.j   3.%252B1.j   4.%252B1.j   5.%252B1.j   6.%252B1.j   7.%252B1.j%250A   8.%252B1.j   9.%252B1.j  10.%252B1.j  11.%252B1.j  12.%252B1.j  13.%252B1.j  14.%252B1.j  15.%252B1.j%250A  16.%252B1.j  17.%252B1.j  18.%252B1.j  19.%252B1.j  20.%252B1.j  21.%252B1.j  22.%252B1.j  23.%252B1.j%250A  24.%252B1.j  25.%252B1.j  26.%252B1.j  27.%252B1.j  28.%252B1.j  29.%252B1.j  30.%252B1.j  31.%252B1.j%250A  32.%252B1.j  33.%252B1.j  34.%252B1.j  35.%252B1.j  36.%252B1.j  37.%252B1.j  38.%252B1.j  39.%252B1.j%250A  40.%252B1.j  41.%252B1.j  42.%252B1.j  43.%252B1.j  44.%252B1.j  45.%252B1.j  46.%252B1.j  47.%252B1.j%250A  48.%252B1.j  49.%252B1.j  50.%252B1.j  51.%252B1.j  52.%252B1.j  53.%252B1.j  54.%252B1.j  55.%252B1.j%250A  56.%252B1.j  57.%252B1.j  58.%252B1.j  59.%252B1.j  60.%252B1.j  61.%252B1.j  62.%252B1.j  63.%252B1.j%250A  64.%252B1.j  65.%252B1.j  66.%252B1.j  67.%252B1.j  68.%252B1.j  69.%252B1.j  70.%252B1.j  71.%252B1.j%250A  72.%252B1.j  73.%252B1.j  74.%252B1.j  75.%252B1.j  76.%252B1.j  77.%252B1.j  78.%252B1.j  79.%252B1.j%250A  80.%252B1.j  81.%252B1.j  82.%252B1.j  83.%252B1.j  84.%252B1.j  85.%252B1.j  86.%252B1.j  87.%252B1.j%250A  88.%252B1.j  89.%252B1.j  90.%252B1.j  91.%252B1.j  92.%252B1.j  93.%252B1.j  94.%252B1.j  95.%252B1.j%250A  96.%252B1.j  97.%252B1.j  98.%252B1.j  99.%252B1.j%255D" isContainer="True" />',
            '<var name="bigarray" type="ndarray" qualifier="numpy" value="ndarray%253A %255B%255B    0     1     2 ...%252C  9997  9998  9999%255D%250A %255B10000 10001 10002 ...%252C 19997 19998 19999%255D%250A %255B20000 20001 20002 ...%252C 29997 29998 29999%255D%250A ...%252C %250A %255B70000 70001 70002 ...%252C 79997 79998 79999%255D%250A %255B80000 80001 80002 ...%252C 89997 89998 89999%255D%250A %255B90000 90001 90002 ...%252C 99997 99998 99999%255D%255D" isContainer="True" />',
            '<var name="hugearray" type="ndarray" qualifier="numpy" value="ndarray%253A %255B      0       1       2 ...%252C 9999997 9999998 9999999%255D" isContainer="True" />',
        ))

        # For each variable, check each of the resolved (meta data) attributes...
        self.write_get_variable(thread_id, frame_id, 'smallarray')
        self.wait_for_multiple_vars((
            '<var name="min" type="complex128"',
            '<var name="max" type="complex128"',
            '<var name="shape" type="tuple"',
            '<var name="dtype" type="dtype"',
            '<var name="size" type="int"',
        ))
        # ...and check that the internals are resolved properly
        self.write_get_variable(thread_id, frame_id, 'smallarray\t__internals__')
        self.wait_for_var('<var name="%27size%27')

        self.write_get_variable(thread_id, frame_id, 'bigarray')
        # isContainer could be true on some numpy versions, so, we only check for the var begin.
        self.wait_for_multiple_vars((
            [
                '<var name="min" type="int64" qualifier="numpy" value="int64%253A 0"',
                '<var name="min" type="int64" qualifier="numpy" value="int64%3A 0"',
                '<var name="size" type="int" qualifier="{0}" value="int%3A 100000"'.format(builtin_qualifier),
            ],
            [
                '<var name="max" type="int64" qualifier="numpy" value="int64%253A 99999"',
                '<var name="max" type="int32" qualifier="numpy" value="int32%253A 99999"',
                '<var name="max" type="int64" qualifier="numpy" value="int64%3A 99999"',
                '<var name="max" type="int32" qualifier="numpy" value="int32%253A 99999"',
            ],
            '<var name="shape" type="tuple"',
            '<var name="dtype" type="dtype"',
            '<var name="size" type="int"'
        ))
        self.write_get_variable(thread_id, frame_id, 'bigarray\t__internals__')
        self.wait_for_var('<var name="%27size%27')

        # this one is different because it crosses the magic threshold where we don't calculate
        # the min/max
        self.write_get_variable(thread_id, frame_id, 'hugearray')
        self.wait_for_var((
            [
                '<var name="min" type="str" qualifier={0} value="str%253A ndarray too big%252C calculating min would slow down debugging" />'.format(builtin_qualifier),
                '<var name="min" type="str" qualifier={0} value="str%3A ndarray too big%252C calculating min would slow down debugging" />'.format(builtin_qualifier),
                '<var name="min" type="str" qualifier="{0}" value="str%253A ndarray too big%252C calculating min would slow down debugging" />'.format(builtin_qualifier),
                '<var name="min" type="str" qualifier="{0}" value="str%3A ndarray too big%252C calculating min would slow down debugging" />'.format(builtin_qualifier),
            ],
            [
                '<var name="max" type="str" qualifier={0} value="str%253A ndarray too big%252C calculating max would slow down debugging" />'.format(builtin_qualifier),
                '<var name="max" type="str" qualifier={0} value="str%3A ndarray too big%252C calculating max would slow down debugging" />'.format(builtin_qualifier),
                '<var name="max" type="str" qualifier="{0}" value="str%253A ndarray too big%252C calculating max would slow down debugging" />'.format(builtin_qualifier),
                '<var name="max" type="str" qualifier="{0}" value="str%3A ndarray too big%252C calculating max would slow down debugging" />'.format(builtin_qualifier),
            ],
            '<var name="shape" type="tuple"',
            '<var name="dtype" type="dtype"',
            '<var name="size" type="int"',
        ))
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
        self.wait_for_var(['<xml><more>True</more></xml>'])
        self.write_debug_console_expression("%s\t%s\tEVALUATE\t    print(i)" % (thread_id, frame_id))
        self.wait_for_var(['<xml><more>True</more></xml>'])
        self.write_debug_console_expression("%s\t%s\tEVALUATE\t" % (thread_id, frame_id))
        self.wait_for_var(
            [
                '<xml><more>False</more><output message="0"></output><output message="1"></output><output message="2"></output></xml>'            ]
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

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)

        assert line == 2, 'Expected return to be in line 2, was: %s' % line

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

        self.wait_for_breakpoint_hit('108')

        self.write_get_frame(thread_id, frame_id)

        self.wait_for_vars('<xml><var name="variable_for_test_1" type="int" qualifier="{0}" value="int%253A 10" />%0A</xml>'.format(builtin_qualifier))

        self.write_step_over(thread_id)

        self.wait_for_breakpoint_hit('108')


        self.write_get_frame(thread_id, frame_id)

        self.wait_for_vars('<xml><var name="variable_for_test_1" type="int" qualifier="{0}" value="int%253A 10" />%0A<var name="variable_for_test_2" type="int" qualifier="{0}" value="int%253A 20" />%0A</xml>'.format(builtin_qualifier))

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
# WriterThreadCaseQThread2
#=======================================================================================================================
class WriterThreadCaseQThread2(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case_qthread2.py')

    def run(self):
        self.start_socket()
        breakpoint_id = self.write_add_breakpoint(24, 'long_running')
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
        breakpoint_id = self.write_add_breakpoint(22, 'run')
        self.write_make_initial_run()

        thread_id, frame_id = self.wait_for_breakpoint_hit()

        self.write_remove_breakpoint(breakpoint_id)
        self.write_run_thread(thread_id)

        self.log.append('Checking sequence. Found: %s' % (self._sequence))
        assert 9 == self._sequence, 'Expected 9. Had: %s' % self._sequence

        self.log.append('Marking finished ok.')
        self.finished_ok = True

#=======================================================================================================================
# WriterThreadCaseQThread4
#=======================================================================================================================
class WriterThreadCaseQThread4(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case_qthread4.py')

    def run(self):
        self.start_socket()
        breakpoint_id = self.write_add_breakpoint(28, 'on_start') # breakpoint on print('On start called2').
        self.write_make_initial_run()

        thread_id, frame_id = self.wait_for_breakpoint_hit()

        self.write_remove_breakpoint(breakpoint_id)
        self.write_run_thread(thread_id)

        self.log.append('Checking sequence. Found: %s' % (self._sequence))
        assert 9 == self._sequence, 'Expected 9. Had: %s' % self._sequence

        self.log.append('Marking finished ok.')
        self.finished_ok = True

    def additional_output_checks(self, stdout, stderr):
        if 'On start called' not in stdout:
            raise AssertionError('Expected "On start called" to be in stdout:\n%s' % (stdout,))
        if 'Done sleeping' not in stdout:
            raise AssertionError('Expected "Done sleeping" to be in stdout:\n%s' % (stdout,))
        if 'native Qt signal is not callable' in stderr:
            raise AssertionError('Did not expect "native Qt signal is not callable" to be in stderr:\n%s' % (stderr,))

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
# WriterThreadCaseMSwitch
#=======================================================================================================================
class WriterThreadCaseMSwitch(debugger_unittest.AbstractWriterThread):

    TEST_FILE = 'tests_pydevd_python._debugger_case_m_switch'
    IS_MODULE = True

    def get_environ(self):
        env = os.environ.copy()
        curr_pythonpath = env.get('PYTHONPATH', '')

        root_dirname = os.path.dirname(os.path.dirname(__file__))

        curr_pythonpath += root_dirname + os.pathsep
        env['PYTHONPATH'] = curr_pythonpath
        return env

    def get_main_filename(self):
        return debugger_unittest._get_debugger_test_file('_debugger_case_m_switch.py')

    def run(self):
        self.start_socket()

        self.log.append('writing add breakpoint')
        breakpoint_id = self.write_add_breakpoint(1, None)

        self.log.append('making initial run')
        self.write_make_initial_run()

        self.log.append('waiting for breakpoint hit')
        thread_id, frame_id = self.wait_for_breakpoint_hit()

        self.write_remove_breakpoint(breakpoint_id)

        self.log.append('run thread')
        self.write_run_thread(thread_id)

        self.log.append('asserting')
        try:
            assert 9 == self._sequence, 'Expected 9. Had: %s' % self._sequence
        except:
            self.log.append('assert failed!')
            raise
        self.log.append('asserted')

        self.finished_ok = True


# =======================================================================================================================
# WriterThreadCaseModuleWithEntryPoint
# =======================================================================================================================
class WriterThreadCaseModuleWithEntryPoint(WriterThreadCaseMSwitch):
    TEST_FILE = 'tests_pydevd_python._debugger_case_module_entry_point:main'
    IS_MODULE = True

    def get_main_filename(self):
        return debugger_unittest._get_debugger_test_file('_debugger_case_module_entry_point.py')




#=======================================================================================================================
# WriterThreadCaseRemoteDebugger
#=======================================================================================================================
class WriterThreadCaseRemoteDebugger(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case_remote.py')

    def run(self):
        self.start_socket(8787)

        self.log.append('making initial run')
        self.write_make_initial_run()

        self.log.append('waiting for breakpoint hit')
        thread_id, frame_id = self.wait_for_breakpoint_hit('105')

        self.log.append('run thread')
        self.write_run_thread(thread_id)

        self.log.append('asserting')
        try:
            assert 5 == self._sequence, 'Expected 5. Had: %s' % self._sequence
        except:
            self.log.append('assert failed!')
            raise
        self.log.append('asserted')

        self.finished_ok = True

#=======================================================================================================================
# _SecondaryMultiProcProcessWriterThread
#=======================================================================================================================
class _SecondaryMultiProcProcessWriterThread(debugger_unittest.AbstractWriterThread):

    FORCE_KILL_PROCESS_WHEN_FINISHED_OK = True

    def __init__(self, server_socket):
        debugger_unittest.AbstractWriterThread.__init__(self)
        self.server_socket = server_socket

    def run(self):
        print('waiting for second process')
        self.sock, addr = self.server_socket.accept()
        print('accepted second process')

        from tests_pydevd_python.debugger_unittest import ReaderThread
        self.reader_thread = ReaderThread(self.sock)
        self.reader_thread.start()

        self._sequence = -1
        # initial command is always the version
        self.write_version()
        self.log.append('start_socket')
        self.write_make_initial_run()
        time.sleep(.5)
        self.finished_ok = True

#=======================================================================================================================
# WriterThreadCaseRemoteDebuggerMultiProc
#=======================================================================================================================
class WriterThreadCaseRemoteDebuggerMultiProc(debugger_unittest.AbstractWriterThread):

    # It seems sometimes it becomes flaky on the ci because the process outlives the writer thread...
    # As we're only interested in knowing if a second connection was received, just kill the related
    # process.
    FORCE_KILL_PROCESS_WHEN_FINISHED_OK = True

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case_remote_1.py')

    def run(self):
        self.start_socket(8787)

        self.log.append('making initial run')
        self.write_make_initial_run()

        self.log.append('waiting for breakpoint hit')
        thread_id, frame_id = self.wait_for_breakpoint_hit('105')

        self.secondary_multi_proc_process_writer_thread  = secondary_multi_proc_process_writer_thread = \
            _SecondaryMultiProcProcessWriterThread(self.server_socket)
        secondary_multi_proc_process_writer_thread.start()

        self.log.append('run thread')
        self.write_run_thread(thread_id)

        for _i in xrange(400):
            if secondary_multi_proc_process_writer_thread.finished_ok:
                break
            time.sleep(.1)
        else:
            self.log.append('Secondary process not finished ok!')
            raise AssertionError('Secondary process not finished ok!')

        self.log.append('Secondary process finished!')
        try:
            assert 5 == self._sequence, 'Expected 5. Had: %s' % self._sequence
        except:
            self.log.append('assert failed!')
            raise
        self.log.append('asserted')

        self.finished_ok = True

    def do_kill(self):
        debugger_unittest.AbstractWriterThread.do_kill(self)
        if hasattr(self, 'secondary_multi_proc_process_writer_thread'):
            self.secondary_multi_proc_process_writer_thread.do_kill()

#=======================================================================================================================
# WriterThreadCaseTypeExt - [Test Case]: Custom type presentation extensions
#======================================================================================================================
class WriterThreadCaseTypeExt(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case_type_ext.py')

    def run(self):
        self.start_socket()
        self.write_add_breakpoint(7, None)
        self.write_make_initial_run()

        thread_id, frame_id, line = self.wait_for_breakpoint_hit('111', True)
        self.write_get_frame(thread_id, frame_id)
        self.wait_for_var(r'<var name="my_rect" type="Rect" qualifier="__main__" value="Rectangle%255BLength%253A 5%252C Width%253A 10 %252C Area%253A 50%255D" isContainer="True" />') is True
        self.write_get_variable(thread_id, frame_id, 'my_rect')
        self.wait_for_var(r'<var name="area" type="int" qualifier="{0}" value="int%253A 50" />'.format(builtin_qualifier)) is True
        self.write_run_thread(thread_id)
        self.finished_ok = True


    def get_environ(self):
        env = os.environ.copy()

        python_path = env.get("PYTHONPATH","")
        ext_base = debugger_unittest._get_debugger_test_file('my_extensions')
        env['PYTHONPATH']= ext_base + os.pathsep + python_path  if python_path else ext_base
        return env

#=======================================================================================================================
# WriterThreadCaseEventExt - [Test Case]: Test initialize event for extensions
#======================================================================================================================
class WriterThreadCaseEventExt(debugger_unittest.AbstractWriterThread):

    TEST_FILE = debugger_unittest._get_debugger_test_file('_debugger_case_event_ext.py')

    def run(self):
        self.start_socket()
        self.write_make_initial_run()
        self.finished_ok = True

    def additional_output_checks(self, stdout, stderr):
        if 'INITIALIZE EVENT RECEIVED' not in stdout:
            raise AssertionError('No initialize event received')

    def get_environ(self):
        env = os.environ.copy()

        python_path = env.get("PYTHONPATH","")
        ext_base = debugger_unittest._get_debugger_test_file('my_extensions')
        env['PYTHONPATH']= ext_base + os.pathsep + python_path  if python_path else ext_base
        env["VERIFY_EVENT_TEST"] = "1"
        return env

#=======================================================================================================================
# Test
#=======================================================================================================================
class Test(unittest.TestCase, debugger_unittest.DebuggerRunner):

    def get_command_line(self):
        if IS_JYTHON:
            if sys.executable is not None:
                # i.e.: we're running with the provided jython.exe
                return [sys.executable]
            else:


                return [
                    get_java_location(),
                    '-classpath',
                    get_jython_jar(),
                    'org.python.util.jython'
                ]

        if IS_CPYTHON:
            return [sys.executable, '-u']

        if IS_IRONPYTHON:
            return [
                    sys.executable,
                    '-X:Frames'
                ]

        raise RuntimeError('Unable to provide command line')

    @unittest.skipIf(IS_IRONPYTHON, reason='Test needs gc.get_referrers to really check anything.')
    def test_case_1(self):
        self.check_case(WriterThreadCase1)

    def test_case_2(self):
        self.check_case(WriterThreadCase2)

    @unittest.skipIf(IS_IRONPYTHON, reason='This test fails once in a while due to timing issues on IronPython, so, skipping it.')
    def test_case_3(self):
        self.check_case(WriterThreadCase3)

    @unittest.skipIf(IS_JYTHON, reason='This test is flaky on Jython, so, skipping it.')
    def test_case_4(self):
        self.check_case(WriterThreadCase4)

    def test_case_5(self):
        self.check_case(WriterThreadCase5)

    def test_case_6(self):
        self.check_case(WriterThreadCase6)

    @unittest.skipIf(IS_IRONPYTHON, "Different behavior on IronPython")
    def test_case_7(self):
        # This test checks that we start without variables and at each step a new var is created, but on ironpython,
        # the variables exist all at once (with None values), so, we can't test it properly.
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

    @unittest.skipIf(IS_IRONPYTHON, reason='Failing on IronPython (needs to be investigated).')
    def test_case_13(self):
        self.check_case(WriterThreadCase13)

    def test_case_14(self):
        self.check_case(WriterThreadCase14)

    def test_case_15(self):
        self.check_case(WriterThreadCase15)

    @unittest.skipIf(not IS_NUMPY, "numpy not available")
    def test_case_16(self):
        self.check_case(WriterThreadCase16)

    def test_case_17(self):
        self.check_case(WriterThreadCase17)

    def test_case_17a(self):
        self.check_case(WriterThreadCase17a)

    @unittest.skipIf(IS_IRONPYTHON or IS_JYTHON, 'Unsupported assign to local')
    def test_case_18(self):
        self.check_case(WriterThreadCase18)

    def test_case_19(self):
        self.check_case(WriterThreadCase19)

    if TEST_DJANGO:
        def test_case_django(self):
            self.check_case(WriterThreadCaseDjango)

        def test_case_django2(self):
            self.check_case(WriterThreadCaseDjango2)


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
                try:
                    from PyQt5 import QtCore
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

    def test_case_qthread4(self):
        if self._has_qt():
            self.check_case(WriterThreadCaseQThread4)

    def test_m_switch(self):
        self.check_case(WriterThreadCaseMSwitch)

    def test_module_entry_point(self):
        self.check_case(WriterThreadCaseModuleWithEntryPoint)

    @unittest.skip('New behaviour differs from PyDev -- needs to be investigated).')
    def test_case_set_next_statement(self):
        self.check_case(WriterThreadCaseSetNextStatement)

    @unittest.skipIf(IS_IRONPYTHON, reason='Failing on IronPython (needs to be investigated).')
    def test_case_type_ext(self):
        self.check_case(WriterThreadCaseTypeExt)

    @unittest.skipIf(IS_IRONPYTHON, reason='Failing on IronPython (needs to be investigated).')
    def test_case_event_ext(self):
        self.check_case(WriterThreadCaseEventExt)


@unittest.skipIf(not IS_CPYTHON, reason='CPython only test.')
class TestPythonRemoteDebugger(unittest.TestCase, debugger_unittest.DebuggerRunner):

    def get_command_line(self):
        return [sys.executable, '-u']

    def add_command_line_args(self, args):
        return args + [self.writer_thread.TEST_FILE]

    def test_remote_debugger(self):
        self.check_case(WriterThreadCaseRemoteDebugger)

    @unittest.skipIf(IS_PY2, "Skip test for Python 2, because child process sometimes keeps alive")
    def test_remote_debugger2(self):
        self.check_case(WriterThreadCaseRemoteDebuggerMultiProc)


def get_java_location():
    from java.lang import System  # @UnresolvedImport
    jre_dir = System.getProperty("java.home")
    for f in [os.path.join(jre_dir, 'bin', 'java.exe'), os.path.join(jre_dir, 'bin', 'java')]:
        if os.path.exists(f):
            return f
    raise RuntimeError('Unable to find java executable')

def get_jython_jar():
    from java.lang import ClassLoader  # @UnresolvedImport
    cl = ClassLoader.getSystemClassLoader()
    paths = map(lambda url: url.getFile(), cl.getURLs())
    for p in paths:
        if 'jython.jar' in p:
            return p
    raise RuntimeError('Unable to find jython.jar')


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



# c:\bin\jython2.7.0\bin\jython.exe -m py.test tests_python
