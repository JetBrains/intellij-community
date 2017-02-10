import time
import multiprocessing
import subprocess
import sys
import os


def run(name):
    dir_path = os.path.dirname(os.path.realpath(__file__))
    subprocess.Popen(('%s' % sys.executable, os.path.join(dir_path, "test_remote.py"), name, "etc etc"))

if __name__ == '__main__':
    multiprocessing.Process(target=run, args=("subprocess",)).start()
    while True:
        time.sleep(0.1)
