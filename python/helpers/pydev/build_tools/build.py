"""
Helper to build pydevd.

It should:
    * recreate our generated files
    * compile cython deps (properly setting up the environment first).

Note that it's used in the CI to build the cython deps based on the PYDEVD_USE_CYTHON environment variable.
"""
from __future__ import print_function

import glob
import os
import subprocess
import sys
import tempfile

from generate_code import remove_if_exists, root_dir, is_python_64bit, \
    generate_dont_trace_files, generate_cython_module, generate_pep669_module

# noinspection SpellCheckingInspection
BINARY_DIRS = '_pydevd_bundle', '_pydevd_frame_eval'


def validate_pair(ob):
    try:
        if not (len(ob) == 2):
            print("Unexpected result:", ob, file=sys.stderr)
            raise ValueError
    except:
        return False
    return True


def consume(it):
    try:
        while True:
            next(it)
    except StopIteration:
        pass


def get_environment_from_batch_command(env_cmd, initial=None):
    """
    Take a command (either a single command or list of arguments)
    and return the environment created after running that command.
    Note that if the command must be a batch file or .cmd file, or the
    changes to the environment will not be captured.

    If initial is supplied, it is used as the initial environment passed
    to the child process.
    """
    if not isinstance(env_cmd, (list, tuple)):
        env_cmd = [env_cmd]
    if not os.path.exists(env_cmd[0]):
        raise RuntimeError('Error: %s does not exist' % (env_cmd[0],))

    # construct the command that will alter the environment
    env_cmd = subprocess.list2cmdline(env_cmd)
    # create a tag so we can tell in the output when the proc is done
    tag = 'Done running command'
    # construct a cmd.exe command to do accomplish this
    cmd = 'cmd.exe /s /c "{env_cmd} && echo "{tag}" && set"'.format(**vars())
    # launch the process
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, env=initial)
    # parse the output sent to stdout
    lines = proc.stdout
    # consume whatever output occurs until the tag is reached
    for line in lines:
        line = line.decode('utf-8')
        if 'The specified configuration type is missing.' in line:
            raise AssertionError('Error executing %s. View http://blog.ionelmc.ro/2014/12/21/compiling-python-extensions-on-windows/ for details.' % (env_cmd))
        if tag in line:
            break

    def handle_line(l):
        try:
            if sys.version_info[0] > 2:
                # define a way to handle each KEY=VALUE line
                return l.decode('utf-8').rstrip().split('=', 1)
            else:
                # define a way to handle each KEY=VALUE line
                return l.rstrip().split('=', 1)
        except UnicodeDecodeError:
            print("Bad env variable: ", l)

    # parse key/values into pairs
    pairs = map(handle_line, lines)
    # make sure the pairs are valid
    valid_pairs = filter(validate_pair, pairs)
    # construct a dictionary of the pairs
    result = dict(valid_pairs)
    # let the process finish
    proc.communicate()
    return result


def remove_binaries(suffixes):
    for binary_dir in BINARY_DIRS:
        for f in os.listdir(os.path.join(root_dir, binary_dir)):
            for suffix in suffixes:
                if f.endswith(suffix):
                    remove_if_exists(os.path.join(root_dir, binary_dir, f))


def build():
    if '--no-remove-binaries' not in sys.argv:
        remove_binaries(['.pyd', '.so'])

    os.chdir(root_dir)

    env = os.environ.copy()

    if sys.platform == 'win32':
        if sys.version_info[:2] in ((2, 7), (3, 6), (3, 7), (3, 8), (3, 9), (3, 10),
                                    (3, 11), (3, 12)):
            import setuptools  # We have to import it first for the compiler to be found.
            from msvccompiler_wrapper import find_vcvarsall

            vcvarsall = find_vcvarsall()

            if vcvarsall is None or not os.path.exists(vcvarsall):
                raise RuntimeError('Error finding vcvarsall.')

            if is_python_64bit():
                env.update(get_environment_from_batch_command(
                    [vcvarsall, 'amd64'],
                    initial=os.environ.copy()))
            else:
                env.update(get_environment_from_batch_command(
                    [vcvarsall, 'x86'],
                    initial=os.environ.copy()))
        else:
            raise AssertionError('Unable to setup environment for Python: %s' % (sys.version,))

        env['MSSdk'] = '1'
        env['DISTUTILS_USE_SDK'] = '1'

    additional_args = []
    for arg in sys.argv:
        if arg.startswith('--target-pyd-name='):
            additional_args.append(arg)
        if arg.startswith('--target-pyd-frame-eval='):
            additional_args.append(arg)
            break
    else:
        additional_args.append('--force-cython')  # Build always forces Cython!

    if sys.platform.startswith('darwin'):
        macos_cross_compile(additional_args, env)
    else:
        args = [
                   sys.executable,
                   os.path.join(os.path.dirname(__file__), '..', 'setup_cython.py'),
                   'build_ext',
                   '--inplace',
               ] + additional_args

        print('Calling args: %s' % (args,))
        subprocess.check_call(args, env=env)


def check_arch(filepath, expected_arch):
    try:
        result = subprocess.check_output(['lipo', '-info', filepath],
                                         stderr=subprocess.STDOUT).decode()
        if expected_arch not in result:
            raise RuntimeError(
                'Wrong architecture for {filepath}. '.format(filepath=filepath) +
                'Expected {expected_arch}, got: {result}'.format(
                    expected_arch=expected_arch, result=result)
            )
    except subprocess.CalledProcessError as e:
        print('Failed to check architecture of {filepath}: {output}'.format(
            filepath=filepath, output=e.output.decode()))
        raise

def macos_cross_compile(additional_args, env):
    # Cross-compilation for x86 and M1.
    tempdir = tempfile.mkdtemp()
    try:
        # x86_64
        x86_env = env.copy()
        x86_env['ARCHFLAGS'] = '-arch x86_64'
        x86_env['MACOSX_DEPLOYMENT_TARGET'] = '14.3'

        args = [
                   sys.executable,
                   os.path.join(os.path.dirname(__file__), '..', 'setup_cython.py'),
                   'build_ext',
                   '--inplace',
                   '--build-lib=%s/lib.x86' % tempdir,
                   '--build-temp=%s/temp.x86' % tempdir,
                   '--target=x86_64-apple-macos14.3',
                   ] + additional_args
        print('Calling args for x86_64: %s' % (args,))
        subprocess.check_call(args, env=x86_env)

        # arm64
        arm_env = env.copy()
        arm_env['ARCHFLAGS'] = '-arch arm64'
        arm_env['MACOSX_DEPLOYMENT_TARGET'] = '14.3'

        args = [
                   sys.executable,
                   os.path.join(os.path.dirname(__file__), '..', 'setup_cython.py'),
                   'build_ext',
                   '--inplace',
                   '--build-lib=%s/lib.arm64' % tempdir,
                   '--build-temp=%s/temp.arm64' % tempdir,
                   '--target=arm64-apple-macos14.3',
                   ] + additional_args
        print('Calling args for arm64: %s' % (args,))
        subprocess.check_call(args, env=arm_env)

        def ensure_dir_exists(path):
            if not os.path.exists(path):
                os.makedirs(path)

        for ext_dir_x86, ext_dir_arm64 in zip(glob.glob('%s/lib.x86/*' % tempdir),
                                              glob.glob('%s/lib.arm64/*' % tempdir)):
            for shared_lib_x86, shared_lib_arm64 in zip(
                    glob.glob('%s/*' % ext_dir_x86), glob.glob('%s/*' % ext_dir_arm64)):

                if not os.path.exists(shared_lib_x86):
                    raise RuntimeError('x86_64 binary not found: {shared_lib_x86}'.format(shared_lib_x86=shared_lib_x86))
                if not os.path.exists(shared_lib_arm64):
                    raise RuntimeError('arm64 binary not found: {shared_lib_arm64}'.format(shared_lib_arm64=shared_lib_arm64))

                check_arch(shared_lib_x86, 'x86_64')
                check_arch(shared_lib_arm64, 'arm64')

                output_path = '%s/%s/%s' % (root_dir, os.path.basename(ext_dir_x86),
                                            os.path.basename(shared_lib_x86))

                ensure_dir_exists(os.path.dirname(output_path))

                args = [
                    'lipo',
                    '-create',
                    '-output',
                    output_path,
                    shared_lib_x86,
                    shared_lib_arm64,
                ]

                print('Building universal binary:')
                print(' - Output: %s' % output_path)
                print(' - x86_64: %s' % shared_lib_x86)
                print(' - arm64:  %s' % shared_lib_arm64)

                try:
                    subprocess.check_call(args, env=env)
                except subprocess.CalledProcessError as e:
                    print('Failed to create universal binary:')
                    print(' - Command: {0}'.format(" ".join(args)))
                    print(' - Return code: {0}'.format(e.returncode))
                    raise
    finally:
        import shutil
        shutil.rmtree(tempdir, ignore_errors=True)

if __name__ == '__main__':
    use_cython = os.getenv('PYDEVD_USE_CYTHON', None)
    if use_cython == 'YES':
        build()
    elif use_cython == 'NO':
        remove_binaries(['.pyd', '.so'])
    elif use_cython is None:
        # Regular process
        if '--no-regenerate-files' not in sys.argv:
            generate_dont_trace_files()
            generate_cython_module()
            generate_pep669_module()
        build()
    else:
        raise RuntimeError(
            'Unexpected value for PYDEVD_USE_CYTHON: %s (accepted: YES, NO)'
            % (use_cython,)
        )
