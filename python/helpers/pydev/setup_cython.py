'''
A simpler setup version just to compile the speedup module.

It should be used as:

python setup_cython build_ext --inplace

Note: the .c file and other generated files are regenerated from
the .pyx file by running "python build_tools/build.py"
'''

import os
from setuptools import setup
os.chdir(os.path.dirname(os.path.abspath(__file__)))


def process_args(extension_name):
    import sys
    target_pydevd_name = extension_name
    force_cython = False
    for i, arg in enumerate(sys.argv[:]):
        if arg.startswith('--target-pyd-name='):
            del sys.argv[i]
            target_pydevd_name = arg[len('--target-pyd-name='):]
        if arg == '--force-cython':
            del sys.argv[i]
            force_cython = True

    return target_pydevd_name, force_cython


def build_extension(dir_name, extension_name, target_pydevd_name, force_cython):
    pyx_file = os.path.join(os.path.dirname(__file__), dir_name, "%s.pyx" % (extension_name,))

    if target_pydevd_name != extension_name:
        # It MUST be there in this case!
        # (otherwise we'll have unresolved externals because the .c file had another name initially).
        import shutil

        # We must force cython in this case (but only in this case -- for the regular setup in the user machine, we
        # should always compile the .c file).
        force_cython = True

        new_pyx_file = os.path.join(os.path.dirname(__file__), dir_name, "%s.pyx" % (target_pydevd_name,))
        new_c_file = os.path.join(os.path.dirname(__file__), dir_name, "%s.c" % (target_pydevd_name,))
        shutil.copy(pyx_file, new_pyx_file)
        pyx_file = new_pyx_file
        assert os.path.exists(pyx_file)

    try:
        if force_cython:
            from Cython.Build import cythonize  # @UnusedImport
            ext_modules = cythonize([
                "%s/%s.pyx" % (dir_name, target_pydevd_name,),
            ])
        else:
            # Always compile the .c (and not the .pyx) file (which we should keep up-to-date by running build_tools/build.py).
            from distutils.extension import Extension
            ext_modules = [Extension('%s.%s' % (dir_name, target_pydevd_name,), [
                "%s/%s.c" % (dir_name, target_pydevd_name,),
            ])]

        setup(
            name='Cythonize',
            ext_modules=ext_modules
        )
    finally:
        if target_pydevd_name != extension_name:
            try:
                os.remove(new_pyx_file)
            except:
                import traceback
                traceback.print_exc()
            try:
                os.remove(new_c_file)
            except:
                import traceback
                traceback.print_exc()


extension_name = "pydevd_cython"
target_pydevd_name, force_cython = process_args(extension_name)
build_extension("_pydevd_bundle", extension_name, target_pydevd_name, force_cython)
