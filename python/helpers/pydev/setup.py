r'''
Full setup, used to distribute the debugger backend to PyPi.

Note that this is mostly so that users can do:

pip install pydevd

in a machine for doing remote-debugging, as a local installation with the IDE should have
everything already distributed.

Reference on wheels:
https://hynek.me/articles/sharing-your-labor-of-love-pypi-quick-and-dirty/
http://lucumr.pocoo.org/2014/1/27/python-on-wheels/

Another (no wheels): https://jamie.curle.io/blog/my-first-experience-adding-package-pypi/

See:

build_tools\pydevd_release_process.txt

for release process.
'''


import os
import sys
from distutils.extension import Extension

from setuptools import setup
from setuptools.dist import Distribution


class BinaryDistribution(Distribution):
    def is_pure(self):
        return False


data_files = []


def accept_file(f):
    f = f.lower()
    for ext in '.py .dll .so .dylib .txt .cpp .h .bat .c .sh .md .txt'.split():
        if f.endswith(ext):
            return True

    return f in ['readme', 'makefile']


def add_directory_to_datafiles(datafiles, dir):
    datafiles.append((dir, [os.path.join(dir, f) for f in os.listdir(dir) if accept_file(f)]))
    for root, dirs, files in os.walk(dir):
        for d in dirs:
            datafiles.append((os.path.join(root, d), [os.path.join(root, d, f) for f in os.listdir(os.path.join(root, d)) if accept_file(f)]))


def accept_extension(f):
    f = f.lower()
    for ext in '.pyd .so'.split():
        if f.endswith(ext):
            return True
    return False


def add_extensions_to_datafiles(datafiles, dir):
    datafiles.append((dir, [os.path.join(dir, f) for f in os.listdir(dir) if accept_extension(f)]))


add_directory_to_datafiles(data_files, 'pydevd_attach_to_process')
add_extensions_to_datafiles(data_files, '_pydevd_bundle')
add_extensions_to_datafiles(data_files, '_pydevd_frame_eval')


def _get_version_from_file():
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'VERSION')) as version_file:
        version_str = version_file.read().strip()
    return version_str


def _replace_version_placeholder_in_file(filepath, version_str, version_placeholder="@@BUILD_NUMBER@@"):
    with open(filepath, 'r') as file:
        file_text = file.read()
    result = file_text.replace(version_placeholder, version_str)
    with open(filepath, 'w') as file:
        file.write(result)


def _replace_version_placeholder(version_str):
    pydevd_filepath = os.path.dirname(os.path.abspath(__file__))
    pydevd_comm_filepath = os.path.join(os.path.join(pydevd_filepath, '_pydevd_bundle'), 'pydevd_comm.py')
    _replace_version_placeholder_in_file(pydevd_comm_filepath, version_str)


version = _get_version_from_file()
_replace_version_placeholder(version)

here = os.path.abspath(os.path.dirname(__file__))
try:
    README = open(os.path.join(here, 'README.rst')).read()
except IOError:
    README = ''

args = dict(
    name='pydevd-pycharm',
    version=version,
    description='PyCharm Debugger (used in PyCharm and PyDev)',
    long_description=README,
    author='JetBrains, Fabio Zadrozny and others',
    url='https://github.com/JetBrains/intellij-community',
    license='Apache 2.0',
    packages=[
        '_pydev_bundle',
        '_pydev_imps',
        '_pydev_runfiles',
        '_pydevd_bundle',
        '_pydevd_frame_eval',
        'pydev_ipython',
        # 'pydev_sitecustomize', -- Not actually a package (not added)
        'pydevd_attach_to_process',
        'pydevd_concurrency_analyser',
        'pydevd_plugins',
        'pydevd_plugins.extensions',
    ],
    py_modules=[
        # 'interpreterInfo', -- Not needed for debugger
        # 'pycompletionserver', -- Not needed for debugger
        'pydev_app_engine_debug_startup',
        # 'pydev_coverage', -- Not needed for debugger
        # 'pydev_pysrc', -- Not needed for debugger
        'pydevconsole',
        'pydevd_file_utils',
        'pydevd',
        'pydevd_pycharm',
        'pydevd_tracing',
        # 'runfiles', -- Not needed for debugger
        'setup_cython',  # Distributed to clients. See: https://github.com/fabioz/PyDev.Debugger/issues/102
        # 'setup', -- Should not be included as a module
    ],
    classifiers=[
        'Development Status :: 6 - Mature',
        'Environment :: Console',
        'Intended Audience :: Developers',

        'License :: OSI Approved :: Apache Software License',

        'Operating System :: MacOS :: MacOS X',
        'Operating System :: Microsoft :: Windows',
        'Operating System :: POSIX',
        'Programming Language :: Python',
        'Programming Language :: Python :: 2',
        'Programming Language :: Python :: 2.7',
        'Programming Language :: Python :: 3',
        'Programming Language :: Python :: 3.4',
        'Programming Language :: Python :: 3.5',
        'Programming Language :: Python :: 3.6',
        'Programming Language :: Python :: 3.7',
        'Programming Language :: Python :: 3.8',
        'Programming Language :: Python :: 3.9',
        'Topic :: Software Development :: Debuggers',
    ],
    entry_points={
        'console_scripts': [
            'pydevd = pydevd:main',
        ],
    },
    data_files=data_files,
    keywords=['pydev', 'pydevd', 'pydev.debugger', 'pycharm'],
    include_package_data=True,
    zip_safe=False,
)

args_with_binaries = args.copy()

if sys.platform not in ('darwin', 'win32'):
    args_with_binaries.update(dict(
        distclass=BinaryDistribution,
        ext_modules=[
            # In this setup, don't even try to compile with cython, just go with the .c file which should've
            # been properly generated from a tested version.
            Extension('_pydevd_bundle.pydevd_cython', ['_pydevd_bundle/pydevd_cython.c',])
        ]
    ))
    if sys.version_info >= (3, 6):
        args_with_binaries.update(dict(
            distclass=BinaryDistribution,
            ext_modules=[
                # In this setup, don't even try to compile with cython, just go with the .c file which should've
                # been properly generated from a tested version.
                Extension('_pydevd_frame_eval.pydevd_frame_evaluator', ['_pydevd_frame_eval/pydevd_frame_evaluator.c',])
            ]
        ))

try:
    setup(**args_with_binaries)
except:
    # Compile failed: just setup without compiling cython deps.
    setup(**args)
    sys.stdout.write('Plain-python version of pydevd-pycharm installed (cython speedups not available).\n')
