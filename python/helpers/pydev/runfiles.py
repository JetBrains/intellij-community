'''
Entry point module (keep at root):

Used to run with tests with unittest/pytest/nose.
'''


import os
try:
    xrange
except:
    xrange = range

def main():
    import sys

    # Separate the nose params and the pydev params.
    pydev_params = []
    other_test_framework_params = []
    found_other_test_framework_param = None

    NOSE_PARAMS = '--nose-params'
    PY_TEST_PARAMS = '--py-test-params'

    for arg in sys.argv[1:]:
        if not found_other_test_framework_param and arg != NOSE_PARAMS and arg != PY_TEST_PARAMS:
            pydev_params.append(arg)

        else:
            if not found_other_test_framework_param:
                found_other_test_framework_param = arg
            else:
                other_test_framework_params.append(arg)


    # Here we'll run either with nose or with the pydev_runfiles.
    from _pydev_runfiles import pydev_runfiles
    from _pydev_runfiles import pydev_runfiles_xml_rpc
    from _pydevd_bundle import pydevd_constants
    from pydevd_file_utils import _NormFile

    DEBUG = 0
    if DEBUG:
        sys.stdout.write('Received parameters: %s\n' % (sys.argv,))
        sys.stdout.write('Params for pydev: %s\n' % (pydev_params,))
        if found_other_test_framework_param:
            sys.stdout.write('Params for test framework: %s, %s\n' % (found_other_test_framework_param, other_test_framework_params))

    try:
        configuration = pydev_runfiles.parse_cmdline([sys.argv[0]] + pydev_params)
    except:
        sys.stderr.write('Command line received: %s\n' % (sys.argv,))
        raise
    pydev_runfiles_xml_rpc.initialize_server(configuration.port)  # Note that if the port is None, a Null server will be initialized.

    NOSE_FRAMEWORK = 1
    PY_TEST_FRAMEWORK = 2
    try:
        if found_other_test_framework_param:
            test_framework = 0  # Default (pydev)
            if found_other_test_framework_param == NOSE_PARAMS:
                import nose
                test_framework = NOSE_FRAMEWORK

            elif found_other_test_framework_param == PY_TEST_PARAMS:
                import pytest
                test_framework = PY_TEST_FRAMEWORK

            else:
                raise ImportError()

        else:
            raise ImportError()

    except ImportError:
        if found_other_test_framework_param:
            sys.stderr.write('Warning: Could not import the test runner: %s. Running with the default pydev unittest runner instead.\n' % (
                found_other_test_framework_param,))

        test_framework = 0

    # Clear any exception that may be there so that clients don't see it.
    # See: https://sourceforge.net/tracker/?func=detail&aid=3408057&group_id=85796&atid=577329
    if hasattr(sys, 'exc_clear'):
        sys.exc_clear()

    if test_framework == 0:

        return pydev_runfiles.main(configuration)  # Note: still doesn't return a proper value.

    else:
        # We'll convert the parameters to what nose or py.test expects.
        # The supported parameters are:
        # runfiles.py  --config-file|-t|--tests <Test.test1,Test2>  dirs|files --nose-params xxx yyy zzz
        # (all after --nose-params should be passed directly to nose)

        # In java:
        # --tests = Constants.ATTR_UNITTEST_TESTS
        # --config-file = Constants.ATTR_UNITTEST_CONFIGURATION_FILE


        # The only thing actually handled here are the tests that we want to run, which we'll
        # handle and pass as what the test framework expects.

        py_test_accept_filter = {}
        files_to_tests = configuration.files_to_tests

        if files_to_tests:
            # Handling through the file contents (file where each line is a test)
            files_or_dirs = []
            for file, tests in files_to_tests.items():
                if test_framework == NOSE_FRAMEWORK:
                    for test in tests:
                        files_or_dirs.append(file + ':' + test)

                elif test_framework == PY_TEST_FRAMEWORK:
                    file = _NormFile(file)
                    py_test_accept_filter[file] = tests
                    files_or_dirs.append(file)

                else:
                    raise AssertionError('Cannot handle test framework: %s at this point.' % (test_framework,))

        else:
            if configuration.tests:
                # Tests passed (works together with the files_or_dirs)
                files_or_dirs = []
                for file in configuration.files_or_dirs:
                    if test_framework == NOSE_FRAMEWORK:
                        for t in configuration.tests:
                            files_or_dirs.append(file + ':' + t)

                    elif test_framework == PY_TEST_FRAMEWORK:
                        file = _NormFile(file)
                        py_test_accept_filter[file] = configuration.tests
                        files_or_dirs.append(file)

                    else:
                        raise AssertionError('Cannot handle test framework: %s at this point.' % (test_framework,))
            else:
                # Only files or dirs passed (let it do the test-loading based on those paths)
                files_or_dirs = configuration.files_or_dirs

        argv = other_test_framework_params + files_or_dirs


        if test_framework == NOSE_FRAMEWORK:
            # Nose usage: http://somethingaboutorange.com/mrl/projects/nose/0.11.2/usage.html
            # show_stdout_option = ['-s']
            # processes_option = ['--processes=2']
            argv.insert(0, sys.argv[0])
            if DEBUG:
                sys.stdout.write('Final test framework args: %s\n' % (argv[1:],))

            from _pydev_runfiles import pydev_runfiles_nose
            PYDEV_NOSE_PLUGIN_SINGLETON = pydev_runfiles_nose.start_pydev_nose_plugin_singleton(configuration)
            argv.append('--with-pydevplugin')
            # Return 'not' because it will return 'success' (so, exit == 0 if success)
            return not nose.run(argv=argv, addplugins=[PYDEV_NOSE_PLUGIN_SINGLETON])

        elif test_framework == PY_TEST_FRAMEWORK:
            if DEBUG:
                sys.stdout.write('Final test framework args: %s\n' % (argv,))
                sys.stdout.write('py_test_accept_filter: %s\n' % (py_test_accept_filter,))

            def dotted(p):
                # Helper to convert path to have dots instead of slashes
                return os.path.normpath(p).replace(os.sep, "/").replace('/', '.')

            curr_dir = os.path.realpath('.')
            curr_dotted = dotted(curr_dir) + '.'

            # Overcome limitation on py.test:
            # When searching conftest if we have a structure as:
            # /my_package
            # /my_package/conftest.py
            # /my_package/tests
            # /my_package/tests/test_my_package.py
            # The test_my_package won't have access to the conftest contents from the
            # test_my_package.py file unless the working dir is set to /my_package.
            #
            # See related issue (for which we work-around below):
            # https://bitbucket.org/hpk42/pytest/issue/639/conftest-being-loaded-twice-giving

            for path in sys.path:
                path_dotted = dotted(path)
                if curr_dotted.startswith(path_dotted):
                    os.chdir(path)
                    break

            for i in xrange(len(argv)):
                arg = argv[i]
                # Workaround bug in py.test: if we pass the full path it ends up importing conftest
                # more than once (so, always work with relative paths).
                if os.path.isfile(arg) or os.path.isdir(arg):
                    from _pydev_bundle.pydev_imports import relpath
                    try:
                        # May fail if on different drives
                        arg = relpath(arg)
                    except ValueError:
                        pass
                    else:
                        argv[i] = arg

            # To find our runfile helpers (i.e.: plugin)...
            d = os.path.dirname(__file__)
            if d not in sys.path:
                sys.path.insert(0, d)

            import pickle, zlib, base64

            # Update environment PYTHONPATH so that it finds our plugin if using xdist.
            os.environ['PYTHONPATH'] = os.pathsep.join(sys.path)

            # Set what should be skipped in the plugin through an environment variable
            s = base64.b64encode(zlib.compress(pickle.dumps(py_test_accept_filter)))
            if pydevd_constants.IS_PY3K:
                s = s.decode('ascii')  # Must be str in py3.
            os.environ['PYDEV_PYTEST_SKIP'] = s

            # Identifies the main pid (i.e.: if it's not the main pid it has to connect back to the
            # main pid to give xml-rpc notifications).
            os.environ['PYDEV_MAIN_PID'] = str(os.getpid())
            os.environ['PYDEV_PYTEST_SERVER'] = str(configuration.port)

            argv.append('-p')
            argv.append('_pydev_runfiles.pydev_runfiles_pytest2')
            if 'unittest' in sys.modules or 'unittest2' in sys.modules:
                sys.stderr.write('pydev test runner error: imported unittest before running pytest.main\n')
            return pytest.main(argv)

        else:
            raise AssertionError('Cannot handle test framework: %s at this point.' % (test_framework,))


if __name__ == '__main__':
    try:
        main()
    finally:
        try:
            # The server is not a daemon thread, so, we have to ask for it to be killed!
            from _pydev_runfiles import pydev_runfiles_xml_rpc
            pydev_runfiles_xml_rpc.force_server_kill()
        except:
            pass  # Ignore any errors here

    import sys
    import threading
    if hasattr(sys, '_current_frames') and hasattr(threading, 'enumerate'):
        import time
        import traceback

        class DumpThreads(threading.Thread):
            def run(self):
                time.sleep(10)

                thread_id_to_name = {}
                try:
                    for t in threading.enumerate():
                        thread_id_to_name[t.ident] = '%s  (daemon: %s)' % (t.name, t.daemon)
                except:
                    pass

                stack_trace = [
                    '===============================================================================',
                    'pydev pyunit runner: Threads still found running after tests finished',
                    '================================= Thread Dump =================================']

                for thread_id, stack in sys._current_frames().items():
                    stack_trace.append('\n-------------------------------------------------------------------------------')
                    stack_trace.append(" Thread %s" % thread_id_to_name.get(thread_id, thread_id))
                    stack_trace.append('')

                    if 'self' in stack.f_locals:
                        sys.stderr.write(str(stack.f_locals['self']) + '\n')

                    for filename, lineno, name, line in traceback.extract_stack(stack):
                        stack_trace.append(' File "%s", line %d, in %s' % (filename, lineno, name))
                        if line:
                            stack_trace.append("   %s" % (line.strip()))
                stack_trace.append('\n=============================== END Thread Dump ===============================')
                sys.stderr.write('\n'.join(stack_trace))


        dump_current_frames_thread = DumpThreads()
        dump_current_frames_thread.setDaemon(True)  # Daemon so that this thread doesn't halt it!
        dump_current_frames_thread.start()
