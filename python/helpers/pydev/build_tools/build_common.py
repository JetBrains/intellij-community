import os
import subprocess
import sys

__all__ = ['ensure_interpreters', 'regenerate_binaries']

from build_tools.build import BINARY_DIRS

root_dir = os.path.dirname(os.path.dirname(__file__))

IS_MACOS = sys.platform.startswith('darwin')
IS_WINDOWS = sys.platform.startswith('win32')


def extract_version(python_install):
    if IS_MACOS:
        return python_install.split('/')[-3][2:]
    elif IS_WINDOWS:
        return python_install.split('\\')[-3][2:]
    raise RuntimeError("Unsupported platform '%s'." % sys.platform)


def list_binaries():
    binary_suffix = None
    if IS_MACOS:
        binary_suffix = '.so'
    elif IS_WINDOWS:
        binary_suffix = '.pyd'

    if binary_suffix is None:
        raise RuntimeError("Unsupported platform '%s'." % sys.platform)

    for binary_dir in BINARY_DIRS:
        for f in os.listdir(os.path.join(root_dir, binary_dir)):
            if f.endswith('.so'):
                yield f


def ensure_interpreters(python_installations):
    for python_install in python_installations:
        assert os.path.exists(python_install), \
            "'%s' interpreter does not exist." % python_install


def regenerate_binaries(python_installations):
    for f in list_binaries():
        raise AssertionError('Binary not removed: %s' % (f,))

    for i, python_install in enumerate(python_installations):
        new_name = 'pydevd_cython_%s_%s' % (sys.platform,
                                            extract_version(python_install))
        args = [
            python_install,
            os.path.join(root_dir, 'build_tools', 'build.py'),
            '--no-remove-binaries',
            '--target-pyd-name=%s' % new_name,
            '--force-cython'
        ]

        if i != 0:
            args.append('--no-regenerate-files')

        version_number = extract_version(python_install)

        if version_number.startswith('36') or version_number.startswith('37') or \
                version_number.startswith('38') or version_number.startswith('39') \
                or version_number.startswith('310'):
            name_frame_eval = 'pydevd_frame_evaluator_%s_%s' % (
                sys.platform, extract_version(python_install))
            args.append('--target-pyd-frame-eval=%s' % name_frame_eval)

        print('Calling: %s' % (' '.join(args)))
        subprocess.check_call(args)
