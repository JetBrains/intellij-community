#  Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import os
import sys
import subprocess

IS_WINDOWS = sys.platform == 'win32'
python_envs_path = os.getenv('ENVS_DIRECTORY')
if IS_WINDOWS:
    python_installations = [
        r'%s\py27_64\Scripts\python.exe' % python_envs_path,
        r'%s\py36_64\Scripts\python.exe' % python_envs_path,
        r'%s\py37_64\Scripts\python.exe' % python_envs_path,
        r'%s\py38_64\Scripts\python.exe' % python_envs_path,
        r'%s\py39_64\Scripts\python.exe' % python_envs_path,
        r'%s\py310_64\Scripts\python.exe' % python_envs_path,
        r'%s\py311_64\Scripts\python.exe' % python_envs_path,
    ]
else:
    python_installations = [
        r'%s/py27_64/bin/python' % python_envs_path,
        r'%s/py36_64/bin/python' % python_envs_path,
        r'%s/py37_64/bin/python' % python_envs_path,
        r'%s/py38_64/bin/python' % python_envs_path,
        r'%s/py39_64/bin/python' % python_envs_path,
        r'%s/py310_64/bin/python' % python_envs_path,
        r'%s/py311_64/bin/python' % python_envs_path,
    ]
GENERATOR_FILE_NAME = "generate_import_error.py"
All_ERRORS_LINES_FILE = os.path.join(os.path.dirname(__file__), 'error_lines.txt')
RESULT_FILE = os.path.join(os.path.dirname(__file__), 'result.txt')
RUN_GEN_STRING = 'Run generation with python: %s'
GEN_FINISHED_STRING = 'Generation finished'


def compute_result_output():
    with open(All_ERRORS_LINES_FILE, 'r') as res_file:
        lines = res_file.readlines()
    out = set()
    for line in lines:
        out.add(line)

    # if we have results from another OS
    try:
        with open(RESULT_FILE, 'r') as res_file:
            lines = res_file.readlines()
        for line in lines:
            out.add(line)
    except:
        pass

    with open(RESULT_FILE, 'w') as res_file:
        for line in out:
            res_file.write(line)
    os.remove(os.path.join(All_ERRORS_LINES_FILE))


if __name__ == '__main__':
    print("Pythons: %s", python_installations)
    for python in python_installations:
        print(RUN_GEN_STRING % python)
        p = subprocess.Popen([python, os.path.join(os.path.dirname(__file__), GENERATOR_FILE_NAME)])
        p.wait()
        print(GEN_FINISHED_STRING)

    compute_result_output()
