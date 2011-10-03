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
if coverage_file:
    os.environ['COVERAGE_FILE'] = coverage_file
if run_cov:
    a_file = open(coverage_file + '.syspath.txt', mode='w')
    a_file.write(os.getcwd()+"\n")
    for path in sys.path: a_file.write(path + "\n")
    a_file.close()
main()
if run_cov:
    main(["xml", "-o", coverage_file + ".xml"])