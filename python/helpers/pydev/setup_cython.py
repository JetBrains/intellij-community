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
                    target_arch, extended=False):
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
            ), force=True)
        else:
            # Always compile the .c (and not the .pyx) file (which we should keep
            # up-to-date by running build_tools/build.py).
            ext_modules = [Extension(
                "%s%s.%s" % (dir_name, "_ext" if extended else "", target_pydevd_name),
                [os.path.join(dir_name, "%s.c" % target_pydevd_name), ],
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
        else:
            if force_cython and extension_name == "pydevd_frame_evaluator":
                # Store the updated version-specific C file.
                new_c_file = os.path.join(os.path.dirname(__file__), dir_name,
                                          "%s.c" % (extension_name,))
                shutil.copy(new_c_file, os.path.join(
                    _find_cython_module_dir(), "pydevd_frame_evaluator.c"))


_frame_evaluator_cython_mod_dir = None


def _find_cython_module_dir():
    """Finds the version-specific frame evaluator Cython module directory."""
    global _frame_evaluator_cython_mod_dir
    if _frame_evaluator_cython_mod_dir:
        return _frame_evaluator_cython_mod_dir
    cython_modules_dir = os.path.join(_pydevd_dir, "_pydevd_frame_eval", "cython")
    major_version, minor_version = sys.version_info[:2]
    for subdir in os.listdir(cython_modules_dir):
        if not os.path.isdir(os.path.join(cython_modules_dir, subdir)):
            continue
        start, end = subdir.split('_')
        start, end = int(start[1:]), int(end[1:])
        if start <= minor_version <= end:
            _frame_evaluator_cython_mod_dir =  os.path.join(cython_modules_dir, subdir)
            return _frame_evaluator_cython_mod_dir
    raise RuntimeError("Failed to find a compatible frame evaluator module"
                       " for Python %d.%d" % (major_version, minor_version))


class FrameEvalModuleBuildContext:
    def __init__(self):
        pydevd_frame_eval_dir_name = "_pydevd_frame_eval"
        self.cython_modules_dir_path = os.path.join(
            _pydevd_dir, pydevd_frame_eval_dir_name, "cython")
        self._pxd_file = os.path.join(
            _pydevd_dir, pydevd_frame_eval_dir_name, "pydevd_frame_evaluator.pxd")
        self._pyx_file = os.path.join(
            _pydevd_dir, pydevd_frame_eval_dir_name, "pydevd_frame_evaluator.pyx")
        self._c_file = os.path.join(
            _pydevd_dir, pydevd_frame_eval_dir_name, "pydevd_frame_evaluator.c")

    def __enter__(self):
        module_dir = _find_cython_module_dir()
        compatible_c = os.path.join(module_dir, "pydevd_frame_evaluator.c")
        shutil.copy(compatible_c, self._c_file)
        compatible_pxd = os.path.join(module_dir, "pydevd_frame_evaluator.pxd")
        compatible_pyx = os.path.join(module_dir, "pydevd_frame_evaluator.pyx")
        shutil.copy(compatible_pxd, self._pxd_file)
        shutil.copy(compatible_pyx, self._pyx_file)

    def __exit__(self, exc_type, exc_val, exc_tb):
        os.remove(self._c_file)
        os.remove(self._pxd_file)
        os.remove(self._pyx_file)


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

    if IS_PY36_OR_GREATER:
        extension_name = "pydevd_frame_evaluator"
        frame_eval_dir_name = "_pydevd_frame_eval"

        target_frame_eval = target_frame_eval or extension_name

        with FrameEvalModuleBuildContext():
            build_extension(frame_eval_dir_name, extension_name, target_frame_eval,
                            force_cython, target_arch, extended)

    if extension_folder:
        create_init_py_files(extension_folder, subdir_names_to_ignore=["build"])


if __name__ == "__main__":
    main()
