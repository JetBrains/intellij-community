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
    with open(coverage_file + '.workdir.txt', mode='w') as a_file:
        a_file.write(os.getcwd())
main()
if run_cov:
    main(["xml", "-o", coverage_file + ".xml"])