"""Coverage.py's main entrypoint."""

import os
import sys

bundled_coverage_path = os.getenv('BUNDLED_COVERAGE_PATH')
if bundled_coverage_path:
    sys_path_backup = sys.path
    sys.path = [p for p in sys.path if p != bundled_coverage_path]
    from coverage.cmdline import main
    sys.path = sys_path_backup
else:
    from coverage.cmdline import main

coverage_file = os.getenv('PYCHARM_COVERAGE_FILE')

coverage_file = coverage_file[0:-len(".coverage")]

run_cov = os.getenv('PYCHARM_RUN_COVERAGE')
if os.getenv('CREATE_TEMP_COVERAGE_FILE'):
    line = 'LOG: PyCharm: File mapping:%s\t%s\n'
    import tempfile
    (h, new_cov_file) = tempfile.mkstemp(prefix='pycharm-coverage')
    print(line%(coverage_file + ".coverage", new_cov_file + ".coverage"))
    print(line%(coverage_file + '.syspath.txt', new_cov_file + '.syspath.txt'))
    print(line%(coverage_file + '.xml', new_cov_file + '.xml'))
    coverage_file = new_cov_file

if coverage_file:
    os.environ['COVERAGE_FILE'] = coverage_file + ".coverage"

if run_cov:
    a_file = open(coverage_file + '.syspath.txt', mode='w')
    a_file.write(os.getcwd()+"\n")
    for path in sys.path: a_file.write(path + "\n")
    a_file.close()

argv = []
for arg in sys.argv:
    if arg.startswith('-m') and arg[2:]:
        argv.append(arg[2:])
    else:
        argv.append(arg)
sys.argv = argv

cwd = os.getcwd()

try:
    main()
finally:
    if run_cov:
        os.chdir(cwd)
        main(["xml", "-o", coverage_file + ".xml", "--ignore-errors"])