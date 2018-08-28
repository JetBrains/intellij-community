pytest_plugins = [
    str('_pytest.pytester'),
]

def _run_and_check(testdir, path):
    result = testdir.runpython(path)
    result.stdout.fnmatch_lines([
        'Worked'
    ])

def test_run(testdir):
    from tests_pydevd_python import debugger_unittest
    from os.path import os
    
    foo_dir = debugger_unittest._get_debugger_test_file(os.path.join('resources', 'launch', 'foo'))
    pydevd_dir = os.path.dirname(os.path.dirname(__file__))
    assert os.path.exists(os.path.join(pydevd_dir, 'pydevd.py'))
    
    _run_and_check(testdir, testdir.makepyfile('''
import sys
sys.path.append(%(pydevd_dir)r)
import pydevd
py_db = pydevd.PyDB()
py_db.ready_to_run = True
py_db.run(%(foo_dir)r)
''' % locals()))
    
    _run_and_check(testdir, testdir.makepyfile('''
import sys
sys.path.append(%(pydevd_dir)r)
import pydevd
py_db = pydevd.PyDB()
py_db.run(%(foo_dir)r, set_trace=False)
''' % locals()))
    
