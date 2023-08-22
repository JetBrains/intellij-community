"""
Creating the needed environments for creating the pre-compiled distribution on Windows:

See:

build_tools\pydevd_release_process.txt

for building binaries/release process.
"""
from __future__ import unicode_literals

import os

from build import remove_binaries
from build_tools.build_common import regenerate_binaries, ensure_interpreters

miniconda32_envs = os.getenv('MINICONDA32_ENVS', r'C:\tools\Miniconda32\envs')
miniconda64_envs = os.getenv('MINICONDA64_ENVS', r'C:\tools\Miniconda\envs')

python_installations = [
    r'%s\py27_32\Scripts\python.exe' % miniconda32_envs,
    r'%s\py36_32\Scripts\python.exe' % miniconda32_envs,
    r'%s\py37_32\Scripts\python.exe' % miniconda32_envs,
    r'%s\py38_32\Scripts\python.exe' % miniconda32_envs,
    r'%s\py39_32\Scripts\python.exe' % miniconda32_envs,
    r'%s\py310_32\Scripts\python.exe' % miniconda32_envs,
    r'%s\py311_32\Scripts\python.exe' % miniconda32_envs,
    r'%s\py312_32\Scripts\python.exe' % miniconda32_envs,

    r'%s\py27_64\Scripts\python.exe' % miniconda64_envs,
    r'%s\py36_64\Scripts\python.exe' % miniconda64_envs,
    r'%s\py37_64\Scripts\python.exe' % miniconda64_envs,
    r'%s\py38_64\Scripts\python.exe' % miniconda64_envs,
    r'%s\py39_64\Scripts\python.exe' % miniconda64_envs,
    r'%s\py310_64\Scripts\python.exe' % miniconda64_envs,
    r'%s\py311_64\Scripts\python.exe' % miniconda64_envs,
    r'%s\py312_64\Scripts\python.exe' % miniconda64_envs,
    ]


def main():
    from generate_code import generate_dont_trace_files
    from generate_code import generate_cython_module

    # First, make sure that our code is up-to-date.
    generate_dont_trace_files()
    generate_cython_module()

    ensure_interpreters(python_installations)

    remove_binaries(['.pyd'])

    regenerate_binaries(python_installations)


if __name__ == '__main__':
    main()

'''
To run do:
cd /D x:\PyDev.Debugger
set PYTHONPATH=x:\PyDev.Debugger
C:\tools\Miniconda32\envs\py27_32\python build_tools\build_binaries_windows.py
'''
