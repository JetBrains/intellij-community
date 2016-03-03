'''
Creating the needed environments for creating the pre-compiled distribution on Windods:

1. Download:

* conda32 at C:\tools\Miniconda32

* conda64 at C:\tools\Miniconda

Create the environments:

C:\tools\Miniconda32\Scripts\conda create -y -f -n py27_32 python=2.7 cython numpy nose ipython pip
C:\tools\Miniconda32\Scripts\activate py27_32
pip install "django>=1.7,<1.8"
pip install -U "setuptools>=0.9"
pip install -U "pip>=1.4" "wheel>=0.21" twine
deactivate

C:\tools\Miniconda32\Scripts\conda create -y -f -n py34_32 python=3.4 cython numpy nose ipython pip
C:\tools\Miniconda32\Scripts\activate py34_32
pip install "django>=1.9"
pip install -U "setuptools>=0.9"
pip install -U "pip>=1.4" "wheel>=0.21" twine
deactivate

C:\tools\Miniconda32\Scripts\conda create -y -f -n py35_32 python=3.5 cython numpy nose ipython pip
C:\tools\Miniconda32\Scripts\activate py35_32
pip install "django>=1.9"
pip install -U "setuptools>=0.9"
pip install -U "pip>=1.4" "wheel>=0.21" twine
deactivate

C:\tools\Miniconda\Scripts\conda create -y -f -n py27_64 python=2.7 cython numpy nose ipython pip
C:\tools\Miniconda\Scripts\activate py27_64
pip install "django>=1.7,<1.8"
pip install -U "setuptools>=0.9"
pip install -U "pip>=1.4" "wheel>=0.21" twine
deactivate

C:\tools\Miniconda\Scripts\conda create -y -f -n py34_64 python=3.4 cython numpy nose ipython pip
C:\tools\Miniconda\Scripts\activate py34_64
pip install "django>=1.9"
pip install -U "setuptools>=0.9"
pip install -U "pip>=1.4" "wheel>=0.21" twine
deactivate

C:\tools\Miniconda\Scripts\conda create -y -f -n py35_64 python=3.5 cython numpy nose ipython pip
C:\tools\Miniconda\Scripts\activate py35_64
pip install "django>=1.9"
pip install -U "setuptools>=0.9"
pip install -U "pip>=1.4" "wheel>=0.21" twine
deactivate


'''

from __future__ import unicode_literals

import os
import subprocess
import sys

miniconda32_envs = os.getenv('MINICONDA32_ENVS', r'C:\tools\Miniconda32\envs')
miniconda64_envs = os.getenv('MINICONDA64_ENVS', r'C:\tools\Miniconda\envs')

python_installations = [
    r'%s\py27_32\python.exe' % miniconda32_envs,
    r'%s\py34_32\python.exe' % miniconda32_envs,
    r'%s\py35_32\python.exe' % miniconda32_envs,

    r'%s\py27_64\python.exe' % miniconda64_envs,
    r'%s\py34_64\python.exe' % miniconda64_envs,
    r'%s\py35_64\python.exe' % miniconda64_envs,
]

root_dir = os.path.dirname(os.path.dirname(__file__))
def list_binaries():
    for f in os.listdir(os.path.join(root_dir, '_pydevd_bundle')):
        if f.endswith('.pyd'):
            yield f

def extract_version(python_install):
    return python_install.split('\\')[-2][2:]


def main():
    from generate_code import generate_dont_trace_files
    from generate_code import generate_cython_module

    # First, make sure that our code is up to date.
    generate_dont_trace_files()
    generate_cython_module()

    for python_install in python_installations:
        assert os.path.exists(python_install)

    from build import remove_binaries
    remove_binaries()

    for f in list_binaries():
        raise AssertionError('Binary not removed: %s' % (f,))

    for i, python_install in enumerate(python_installations):
        new_name = 'pydevd_cython_%s_%s' % (sys.platform, extract_version(python_install))
        args = [
            python_install, os.path.join(root_dir, 'build_tools', 'build.py'), '--no-remove-binaries', '--target-pyd-name=%s' % new_name, '--force-cython']
        if i != 0:
            args.append('--no-regenerate-files')
        print('Calling: %s' % (' '.join(args)))
        subprocess.check_call(args)



if __name__ == '__main__':
    main()

'''
To run do:
cd /D x:\PyDev.Debugger
set PYTHONPATH=x:\PyDev.Debugger
C:\tools\Miniconda32\envs\py27_32\python build_tools\build_binaries_windows.py
'''
