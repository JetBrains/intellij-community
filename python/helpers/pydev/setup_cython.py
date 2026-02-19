"""
A simpler setup version just to compile the speedup module.

It should be used as:

python setup_cython build_ext --inplace

Note: the .c file and other generated files are regenerated from
the .pyx file by running "python build_tools/build.py"
"""
from __future__ import with_statement

import os
import shutil
import sys
from setuptools import setup

_pydevd_dir = os.path.dirname(os.path.abspath(__file__))
os.chdir(_pydevd_dir)

IS_PY36_OR_GREATER = sys.version_info > (3, 6)
IS_PY311_OR_GREATER = sys.version_info > (3, 11)
IS_PY312_OR_GREATER = sys.version_info > (3, 12)


def process_args():
    extension_folder = None
    target_pydevd_name = None
    target_frame_eval = None
    force_cython = False
    target_arch = None

    for i, arg in enumerate(sys.argv[:]):
        if arg == '--build-lib':
            extension_folder = sys.argv[i + 1]
            # It shouldn't be removed from sys.argv (among with --build-temp)
            # because they're passed further to setup().
        if arg.startswith('--target-pyd-name='):
            sys.argv.remove(arg)
            target_pydevd_name = arg[len('--target-pyd-name='):]
        if arg.startswith('--target-pyd-frame-eval='):
            sys.argv.remove(arg)
            target_frame_eval = arg[len('--target-pyd-frame-eval='):]
        if arg == '--force-cython':
            sys.argv.remove(arg)
            force_cython = True
        if arg.startswith('--target='):
            sys.argv.remove(arg)
            target_arch = arg[len('--target='):]

    return extension_folder, target_pydevd_name, target_frame_eval, \
        force_cython, target_arch


def build_extension(dir_name, extension_name, target_pydevd_name, force_cython,
                    target_arch, extended=False, should_add_python_3_12_suffix=True):
    """
    :param dir_name: directory where the Cython file is located
    :param extension_name: name of the .pyx or .c file to build extension from, e.g.
      'pydevd_cython' or 'pydevd_frame_evaluator_39_310'
    :param target_pydevd_name: name of the resulting extension
    :param force_cython: if False, build from the C file, use ``.pyx`` otherwise
    :param target_arch: target architecture, e.g. amd64
    :param extended: add ``_ext`` to the name of the extension package name
    :param should_add_python_3_12_suffix: whether to add a Python 3.12 specific suffixes
      to the build files
    """
    pyx_file = os.path.join(os.path.dirname(__file__), dir_name,
                            "%s.pyx" % (extension_name,))
    has_pxd = False

    if target_pydevd_name != extension_name:
        # It MUST be there in this case! (Otherwise we'll have unresolved externals
        # because the .c file had another name initially).

        # We must force Cython in this case (but only in this case -- for the regular
        # setup in the user machine, we should always compile the .c file).
        force_cython = True

        new_pyx_file = os.path.join(os.path.dirname(__file__), dir_name,
                                    "%s.pyx" % (target_pydevd_name,))
        new_c_file = os.path.join(os.path.dirname(__file__), dir_name,
                                  "%s.c" % (target_pydevd_name,))
        shutil.copy(pyx_file, new_pyx_file)
        pyx_file = new_pyx_file

        pxd_file = os.path.join(os.path.dirname(__file__), dir_name,
                                "%s.pxd" % (extension_name,))
        if os.path.exists(pxd_file) and os.path.isfile(pxd_file):
            new_pxd_file = os.path.join(os.path.dirname(__file__), dir_name,
                                        "%s.pxd" % (target_pydevd_name,))
            shutil.copy(pxd_file, new_pxd_file)
            has_pxd = True

        assert os.path.exists(pyx_file)
        if has_pxd:
            assert os.path.exists(new_pxd_file)

    try:
        from distutils.extension import Extension

        if target_arch:
            extra_compile_args = ["--target=%s" % target_arch]
            extra_link_args = ["--target=%s" % target_arch]
        else:
            extra_compile_args = []
            extra_link_args = []

        if force_cython:
            # noinspection PyPackageRequirements
            from Cython.Build import cythonize
            ext_modules = cythonize(Extension(
                "%s.%s" % (dir_name, target_pydevd_name,),
                ["%s/%s.pyx" % (dir_name, target_pydevd_name,)],
                extra_compile_args=extra_compile_args,
                extra_link_args=extra_link_args,
                ),
                # See: https://cython.readthedocs.io/en/latest/src/userguide/migrating_to_cy30.html#binding-functions
                compiler_directives={'binding': False},
                force=True)
        else:
            # Always compile the .c (and not the .pyx) file (which we should keep
            # up-to-date by running build_tools/build.py).
            ext_modules = [Extension(
                "%s%s.%s" % (dir_name, "_ext" if extended else "", target_pydevd_name),
                [os.path.join(dir_name, extension_name + ".c")],
                # uncomment to generate pdbs for visual studio.
                # extra_compile_args=["-Zi", "/Od"],
                # extra_link_args=["-debug"],
                extra_compile_args=extra_compile_args,
                extra_link_args=extra_link_args,
                )]

        setup(
            name="Cythonize",
            ext_modules=ext_modules
        )
    finally:
        if target_pydevd_name != extension_name:
            try:
                # noinspection PyUnboundLocalVariable
                os.remove(new_pyx_file)
            except:  # noqa: 722
                import traceback
                traceback.print_exc()
            try:
                # noinspection PyUnboundLocalVariable
                os.remove(new_c_file)
            except: # noqa: 722
                import traceback
                traceback.print_exc()
            if has_pxd:
                try:
                    # noinspection PyUnboundLocalVariable
                    os.remove(new_pxd_file)
                except:  # noqa: 722
                    import traceback
                    traceback.print_exc()


def get_frame_eval_extension_name():
    version = sys.version_info[:2]
    if (3, 6) <= version <= (3, 8):
        return "pydevd_frame_evaluator_36_38"
    elif (3, 9) <= version <= (3, 10):
        return "pydevd_frame_evaluator_39_310"
    else:
        raise RuntimeError("Frame evaluation is not supported for the Python version"
                           "%s.%s" % (version[0], version[1]))


def create_init_py_files(extension_folder, subdir_names_to_ignore=None):
    """
    Create `__init__.py` files in every subdirectory of :param:`extension_folder` to
    make sure Python 2 will be able to import from them.
    """
    os.chdir(extension_folder)
    subdir_names_to_ignore = subdir_names_to_ignore or []
    for folder in (f for f in os.listdir(extension_folder)
                   if f not in subdir_names_to_ignore
                      and os.path.isdir(os.path.join(extension_folder, f))):
        file = os.path.join(folder, "__init__.py")
        if not os.path.exists(file):
            open(file, 'a').close()


def main():
    extension_folder, target_pydevd_name, target_frame_eval, force_cython, target_arch \
        = process_args()

    extended = bool(extension_folder)

    extension_name = "pydevd_cython"
    target_pydevd_name = target_pydevd_name or extension_name

    build_extension(
        "_pydevd_bundle", extension_name, target_pydevd_name, force_cython, target_arch,
        extended)

    if IS_PY36_OR_GREATER and not IS_PY311_OR_GREATER:
        extension_name = get_frame_eval_extension_name()
        frame_eval_dir_name = "_pydevd_frame_eval"
        target_frame_eval = target_frame_eval or extension_name

        build_extension(frame_eval_dir_name, extension_name, target_frame_eval,
                        force_cython, target_arch, extended)

    if IS_PY312_OR_GREATER:
        extension_name = "pydevd_pep_669_tracing_cython"
        build_extension("_pydevd_bundle", extension_name, extension_name,
                        force_cython, target_arch, extended, False)

    if extension_folder:
        create_init_py_files(extension_folder, subdir_names_to_ignore=["build"])


if __name__ == "__main__":
    main()
