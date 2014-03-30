"""Coverage.py's main entrypoint."""

import os
import sys
import imp

helpers_root = os.getenv('PYCHARM_HELPERS_ROOT')
if helpers_root:
    sys_path_backup = sys.path
    sys.path = [p for p in sys.path if p!=helpers_root]
    from coverage.cmdline import main
    sys.path = sys_path_backup
else:
    from coverage.cmdline import main

coverage_file = os.getenv('PYCHARM_COVERAGE_FILE')
run_cov = os.getenv('PYCHARM_RUN_COVERAGE')
if os.getenv('JETBRAINS_REMOTE_RUN'):
    line = 'LOG: PyCharm: File mapping:%s\t%s\n'
    import tempfile
    (h, new_cov_file) = tempfile.mkstemp(prefix='pycharm-coverage')
    print(line%(coverage_file, new_cov_file))
    print(line%(coverage_file + '.syspath.txt', new_cov_file + '.syspath.txt'))
    print(line%(coverage_file + '.xml', new_cov_file + '.xml'))
    coverage_file = new_cov_file

if coverage_file:
    os.environ['COVERAGE_FILE'] = coverage_file
if run_cov:
    a_file = open(coverage_file + '.syspath.txt', mode='w')
    a_file.write(os.getcwd()+"\n")
    for path in sys.path: a_file.write(path + "\n")
    a_file.close()
main()
if run_cov:
    main(["xml", "-o", coverage_file + ".xml", "--ignore-errors"])