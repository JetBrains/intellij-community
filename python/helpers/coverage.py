"""Coverage.py's main entrypoint."""
from coverage.cmdline import main
import os
import sys

coverage_file = os.getenv('PYCHARM_COVERAGE_FILE')
os.environ['COVERAGE_FILE'] = coverage_file
with open(coverage_file + '.workdir.txt', mode='w') as a_file:
    a_file.write(os.getcwd())
main()
main(["xml", "-o", coverage_file + ".xml"])


