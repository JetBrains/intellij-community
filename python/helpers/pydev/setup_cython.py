'''
A simpler setup version just to compile the speedup module.

It should be used as:

python setup_cython build_ext --inplace

Note: the .c file and other generated files are regenerated from
the .pyx file by running "python build_tools/build.py"
'''

import os
import sys
from setuptools import setup

os.chdir(os.path.dirname(os.path.abspath(__file__)))


def process_args():
    extension_folder = None
    target_pydevd_name = None
    target_frame_eval = None
    force_cython = False

    for i, arg in enumerate(sys.argv[:]):
        if arg == '--build-lib':
            extension_folder = sys.argv[i + 1]
            # It shouldn't be removed from sys.argv (among with --build-temp) because they're passed further to setup()
        if arg.startswith('--target-pyd-name='):
            sys.argv.remove(arg)
            target_pydevd_name = arg[len('--target-pyd-name='):]
        if arg.startswith('--target-pyd-frame-eval='):
            sys.argv.remove(arg)
            target_frame_eval = arg[len('--target-pyd-frame-eval='):]
        if arg == '--force-cython':
            sys.argv.remove(arg)
            force_cython = True

    return extension_folder, target_pydevd_name, target_frame_eval, force_cython


def build_extension(dir_name, extension_name, target_pydevd_name, force_cython, extended=False, has_pxd=False):
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
        if has_pxd:
            pxd_file = os.path.join(os.path.dirname(__file__), dir_name, "%s.pxd" % (extension_name,))
            new_pxd_file = os.path.join(os.path.dirname(__file__), dir_name, "%s.pxd" % (target_pydevd_name,))
            shutil.copy(pxd_file, new_pxd_file)
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
            ext_modules = [Extension("%s%s.%s" % (dir_name, "_ext" if extended else "", target_pydevd_name,),
                                     [os.path.join(dir_name, "%s.c" % target_pydevd_name), ],
                                     )]

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
            if has_pxd:
                try:
                    os.remove(new_pxd_file)
                except:
                    import traceback
                    traceback.print_exc()


extension_folder, target_pydevd_name, target_frame_eval, force_cython = process_args()

extension_name = "pydevd_cython"
if target_pydevd_name is None:
    target_pydevd_name = extension_name
build_extension("_pydevd_bundle", extension_name, target_pydevd_name, force_cython, extension_folder)

if sys.version_info[:2] == (3, 6):
    extension_name = "pydevd_frame_evaluator"
    if target_frame_eval is None:
        target_frame_eval = extension_name
    build_extension("_pydevd_frame_eval", extension_name, target_frame_eval, force_cython, extension_folder, True)

if extension_folder:
    os.chdir(extension_folder)
    for folder in [file for file in os.listdir(extension_folder) if
                   file != 'build' and os.path.isdir(os.path.join(extension_folder, file))]:
        file = os.path.join(folder, "__init__.py")
        if not os.path.exists(file):
            open(file, 'a').close()
