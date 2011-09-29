"""Coverage.py's main entrypoint."""
from coverage.cmdline import main
import os
import sys

coverage_file = os.getenv('PYCHARM_COVERAGE_FILE')
run_cov = os.getenv('PYCHARM_RUN_COVERAGE')
os.environ['COVERAGE_FILE'] = coverage_file
if run_cov:
    with open(coverage_file + '.workdir.txt', mode='w') as a_file:
        a_file.write(os.getcwd())
main()
if run_cov:
    main(["xml", "-o", coverage_file + ".xml"])


