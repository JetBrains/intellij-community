r'''
Copyright: Brainwy Software Ltda.

License: EPL.
=============

Works for Windows relying on a fork of winappdbg which works in py2/3 (at least for the part we're interested in).

See: https://github.com/fabioz/winappdbg (py3 branch).
Note that the official branch for winappdbg is: https://github.com/MarioVilas/winappdbg, which should be used when it works in Py3.
A private copy is added here to make deployment easier, but changes should always be done upstream first.

Works for Linux relying on gdb.

Limitations:
============

    Linux:
    ------

        1. It possible that ptrace is disabled: /etc/sysctl.d/10-ptrace.conf

        Note that even enabling it in /etc/sysctl.d/10-ptrace.conf (i.e.: making the
        ptrace_scope=0), it's possible that we need to run the application that'll use ptrace (or
        gdb in this case) as root (so, we must sudo the python which'll run this module).

        2. It currently doesn't work in debug builds (i.e.: python_d)


Other implementations:
- pyrasite.com:
    GPL
    Windows/linux (in Linux it also uses gdb to connect -- although specifics are different as we use a dll to execute
    code with other threads stopped). It's Windows approach is more limited because it doesn't seem to deal properly with
    Python 3 if threading is disabled.

- https://github.com/google/pyringe:
    Apache v2.
    Only linux/Python 2.

- http://pytools.codeplex.com:
    Apache V2
    Windows Only (but supports mixed mode debugging)
    Our own code relies heavily on a part of it: http://pytools.codeplex.com/SourceControl/latest#Python/Product/PyDebugAttach/PyDebugAttach.cpp
    to overcome some limitations of attaching and running code in the target python executable on Python 3.
    See: attach.cpp

Linux: References if we wanted to use a pure-python debugger:
    https://bitbucket.org/haypo/python-ptrace/
    http://stackoverflow.com/questions/7841573/how-to-get-an-error-message-for-errno-value-in-python
    Jugaad:
        https://www.defcon.org/images/defcon-19/dc-19-presentations/Jakhar/DEFCON-19-Jakhar-Jugaad-Linux-Thread-Injection.pdf
        https://github.com/aseemjakhar/jugaad

Something else (general and not Python related):
- http://www.codeproject.com/Articles/4610/Three-Ways-to-Inject-Your-Code-into-Another-Proces

Other references:
- https://github.com/haypo/faulthandler
- http://nedbatchelder.com/text/trace-function.html
- https://github.com/python-git/python/blob/master/Python/sysmodule.c (sys_settrace)
- https://github.com/python-git/python/blob/master/Python/ceval.c (PyEval_SetTrace)
- https://github.com/python-git/python/blob/master/Python/thread.c (PyThread_get_key_value)


To build the dlls needed on windows, visual studio express 13 was used (see compile_dll.bat)

See: attach_pydevd.py to attach the pydev debugger to a running python process.
'''

# Note: to work with nasm compiling asm to code and decompiling to see asm with shellcode:
# x:\nasm\nasm-2.07-win32\nasm-2.07\nasm.exe
# nasm.asm&x:\nasm\nasm-2.07-win32\nasm-2.07\ndisasm.exe -b arch nasm
import ctypes
import os
import struct
import subprocess
import sys
import time


SHOW_DEBUG_INFO = 0


def stderr_write(message):
    sys.stderr.write(message)
    sys.stderr.write("\n")


def debug(message):
    if SHOW_DEBUG_INFO > 0:
        stderr_write(message)


class AutoExit(object):

    def __init__(self, on_exit):
        self.on_exit = on_exit

    def __enter__(self):
        pass

    def __exit__(self, *args):
        self.on_exit()


class GenShellCodeHelper(object):

    def __init__(self, is_64):
        from winappdbg import compat
        self.is_64 = is_64
        self._code = []
        if not is_64:
            self._translations = {
                'push esi': b'\x56',
                'push eax': b'\x50',
                'push ebp': b'\x55',
                'push ebx': b'\x53',

                'pop esi': b'\x5E',
                'pop eax': b'\x58',
                'pop ebp': b'\x5D',
                'pop ebx': b'\x5B',

                'mov esi': b'\xBE',
                'mov eax': b'\xB8',
                'mov ebp': b'\xBD',
                'mov ebx': b'\xBB',

                'call ebp': b'\xFF\xD5',
                'call eax': b'\xFF\xD0',
                'call ebx': b'\xFF\xD3',

                'mov ebx,eax': b'\x89\xC3',
                'mov eax,ebx': b'\x89\xD8',
                'mov ebp,esp': b'\x89\xE5',
                'mov esp,ebp': b'\x89\xEC',
                'push dword': b'\x68',

                'mov ebp,eax': b'\x89\xC5',
                'mov eax,ebp': b'\x89\xE8',

                'ret': b'\xc3',
            }
        else:
            # Translate 64 bits
            self._translations = {
                'push rsi': b'\x56',
                'push rax': b'\x50',
                'push rbp': b'\x55',
                'push rbx': b'\x53',
                'push rsp': b'\x54',
                'push rdi': b'\x57',

                'pop rsi': b'\x5E',
                'pop rax': b'\x58',
                'pop rbp': b'\x5D',
                'pop rbx': b'\x5B',
                'pop rsp': b'\x5C',
                'pop rdi': b'\x5F',

                'mov rsi': b'\x48\xBE',
                'mov rax': b'\x48\xB8',
                'mov rbp': b'\x48\xBD',
                'mov rbx': b'\x48\xBB',
                'mov rdi': b'\x48\xBF',
                'mov rcx': b'\x48\xB9',
                'mov rdx': b'\x48\xBA',

                'call rbp': b'\xFF\xD5',
                'call rax': b'\xFF\xD0',
                'call rbx': b'\xFF\xD3',

                'mov rbx,rax': b'\x48\x89\xC3',
                'mov rax,rbx': b'\x48\x89\xD8',
                'mov rbp,rsp': b'\x48\x89\xE5',
                'mov rsp,rbp': b'\x48\x89\xEC',
                'mov rcx,rbp': b'\x48\x89\xE9',

                'mov rbp,rax': b'\x48\x89\xC5',
                'mov rax,rbp': b'\x48\x89\xE8',

                'mov rdi,rbp': b'\x48\x89\xEF',

                'ret': b'\xc3',
            }

    def push_addr(self, addr):
        self._code.append(self.translate('push dword'))
        self._code.append(addr)

    def push(self, register):
        self._code.append(self.translate('push %s' % register))
        return AutoExit(lambda: self.pop(register))

    def pop(self, register):
        self._code.append(self.translate('pop %s' % register))

    def mov_to_register_addr(self, register, addr):
        self._code.append(self.translate('mov %s' % register))
        self._code.append(addr)

    def mov_register_to_from(self, register_to, register_from):
        self._code.append(self.translate('mov %s,%s' % (register_to, register_from)))

    def call(self, register):
        self._code.append(self.translate('call %s' % register))

    def preserve_stack(self):
        self.mov_register_to_from('ebp', 'esp')
        return AutoExit(lambda: self.restore_stack())

    def restore_stack(self):
        self.mov_register_to_from('esp', 'ebp')

    def ret(self):
        self._code.append(self.translate('ret'))

    def get_code(self):
        return b''.join(self._code)

    def translate(self, code):
        return self._translations[code]

    def pack_address(self, address):
        if self.is_64:
            return struct.pack('<q', address)
        else:
            return struct.pack('<L', address)

    def convert(self, code):
        '''
        Note:

        If the shellcode starts with '66' controls, it needs to be changed to add [BITS 32] or
        [BITS 64] to the start.

        To use:

        convert("""
            55
            53
            50
            BDE97F071E
            FFD5
            BDD67B071E
            FFD5
            5D
            5B
            58
            C3
            """)
        '''
        code = code.replace(' ', '')
        lines = []
        for l in code.splitlines(False):
            lines.append(l)
        code = ''.join(lines)  # Remove new lines
        return code.decode('hex')


def resolve_label(process, label):
    max_attempts = 10
    for i in range(max_attempts):
        try:
            address = process.resolve_label(label)
            if not address:
                raise AssertionError('%s not resolved.' % (label,))
            return address
        except:
            try:
                process.scan_modules()
            except:
                pass
            if i == max_attempts - 1:
                raise
            # At most 4 seconds to resolve it.
            time.sleep(4. / max_attempts)


def is_python_64bit():
    return (struct.calcsize('P') == 8)


def is_mac():
    import platform
    return platform.system() == 'Darwin'


def run_python_code_windows(pid, python_code, connect_debugger_tracing=False, show_debug_info=0):
    assert '\'' not in python_code, 'Having a single quote messes with our command.'
    from winappdbg.process import Process
    if not isinstance(python_code, bytes):
        python_code = python_code.encode('utf-8')

    process = Process(pid)
    bits = process.get_bits()
    is_64 = bits == 64

    if is_64 != is_python_64bit():
        raise RuntimeError("The architecture of the Python used to connect doesn't match the architecture of the target.\n"
        "Target 64 bits: %s\n"
        "Current Python 64 bits: %s" % (is_64, is_python_64bit()))

    debug('Connecting to %s bits target' % (bits,))
    assert resolve_label(process, b'PyGILState_Ensure')

    filedir = os.path.dirname(__file__)
    if is_64:
        suffix = 'amd64'
    else:
        suffix = 'x86'
    target_dll = os.path.join(filedir, 'attach_%s.dll' % suffix)
    if not os.path.exists(target_dll):
        raise RuntimeError('Could not find dll file to inject: %s' % target_dll)
    debug('Injecting dll')
    process.inject_dll(target_dll.encode('mbcs'))
    debug('Dll injected')

    process.scan_modules()
    attach_func = resolve_label(process, b'AttachAndRunPythonCode')
    assert attach_func

    debug('Allocating code in target process')
    assert isinstance(python_code, bytes)
    code_address = process.malloc(len(python_code))
    assert code_address
    debug('Writing code in target process')
    process.write(code_address, python_code)

    debug('Allocating return value memory in target process')
    attach_info_address = process.malloc(ctypes.sizeof(ctypes.c_int))
    assert attach_info_address

    CONNECT_DEBUGGER = 2

    attach_info = 0
    if show_debug_info:
        SHOW_DEBUG_INFO = 1
        attach_info |= SHOW_DEBUG_INFO  # Uncomment to show debug info

    if connect_debugger_tracing:
        attach_info |= CONNECT_DEBUGGER

    # Note: previously the attach_info address was treated as read/write to have the return
    # value, but it seems that sometimes when the program wrote back the memory became
    # unreadable with the stack trace below when trying to read, so, we just write and
    # no longer inspect the return value.
    # i.e.:
    # Traceback (most recent call last):
    #   File "X:\pydev\plugins\org.python.pydev.core\pysrc\pydevd_attach_to_process\attach_pydevd.py", line 72, in <module>
    #     main(process_command_line(sys.argv[1:]))
    #   File "X:\pydev\plugins\org.python.pydev.core\pysrc\pydevd_attach_to_process\attach_pydevd.py", line 68, in main
    #     setup['pid'], python_code, connect_debugger_tracing=True, show_debug_info=show_debug_info_on_target_process)
    #   File "X:\pydev\plugins\org.python.pydev.core\pysrc\pydevd_attach_to_process\add_code_to_python_process.py", line 392, in run_python_code_windows
    #     return_code = process.read_int(return_code_address)
    #   File "X:\pydev\plugins\org.python.pydev.core\pysrc\pydevd_attach_to_process\winappdbg\process.py", line 1673, in read_int
    #     return self.__read_c_type(lpBaseAddress, b'@l', ctypes.c_int)
    #   File "X:\pydev\plugins\org.python.pydev.core\pysrc\pydevd_attach_to_process\winappdbg\process.py", line 1568, in __read_c_type
    #     packed = self.read(address, size)
    #   File "X:\pydev\plugins\org.python.pydev.core\pysrc\pydevd_attach_to_process\winappdbg\process.py", line 1598, in read
    #     if not self.is_buffer(lpBaseAddress, nSize):
    #   File "X:\pydev\plugins\org.python.pydev.core\pysrc\pydevd_attach_to_process\winappdbg\process.py", line 2843, in is_buffer
    #     mbi = self.mquery(address)
    #   File "X:\pydev\plugins\org.python.pydev.core\pysrc\pydevd_attach_to_process\winappdbg\process.py", line 2533, in mquery
    #     return win32.VirtualQueryEx(hProcess, lpAddress)
    #   File "X:\pydev\plugins\org.python.pydev.core\pysrc\pydevd_attach_to_process\winappdbg\win32\kernel32.py", line 3742, in VirtualQueryEx
    #     raise ctypes.WinError()
    # PermissionError: [WinError 5] Access is denied.
    # Process finished with exitValue: 1

    process.write_int(attach_info_address, attach_info)

    helper = GenShellCodeHelper(is_64)
    if is_64:
        # Interesting read: http://msdn.microsoft.com/en-us/library/ms235286.aspx
        # Overview of x64 Calling Conventions (for windows: Linux is different!)
        # Register Usage: http://msdn.microsoft.com/en-us/library/9z1stfyw.aspx
        # The registers RAX, RCX, RDX, R8, R9, R10, R11 are considered volatile and must be considered destroyed on function calls (unless otherwise safety-provable by analysis such as whole program optimization).
        #
        # The registers RBX, RBP, RDI, RSI, RSP, R12, R13, R14, and R15 are considered nonvolatile and must be saved and restored by a function that uses them.
        #
        # Important: RCX: first int argument

        with helper.push('rdi'):  # This one REALLY must be pushed/poped
            with helper.push('rsp'):
                with helper.push('rbp'):
                    with helper.push('rbx'):

                        with helper.push('rdi'):  # Note: pop is automatic.
                            helper.mov_to_register_addr('rcx', helper.pack_address(code_address))
                            helper.mov_to_register_addr('rdx', helper.pack_address(attach_info_address))
                            helper.mov_to_register_addr('rbx', helper.pack_address(attach_func))
                            helper.call('rbx')

    else:
        with helper.push('eax'):  # Note: pop is automatic.
            with helper.push('ebp'):
                with helper.push('ebx'):

                    with helper.preserve_stack():
                        # Put our code as a parameter in the stack (on x86, we push parameters to
                        # the stack)
                        helper.push_addr(helper.pack_address(attach_info_address))
                        helper.push_addr(helper.pack_address(code_address))
                        helper.mov_to_register_addr('ebx', helper.pack_address(attach_func))
                        helper.call('ebx')

    helper.ret()

    code = helper.get_code()

    # Uncomment to see the disassembled version of what we just did...
#     with open('f.asm', 'wb') as stream:
#         stream.write(code)
#
#     exe = r'x:\nasm\nasm-2.07-win32\nasm-2.07\ndisasm.exe'
#     if is_64:
#         arch = '64'
#     else:
#         arch = '32'
#
#     subprocess.call((exe + ' -b %s f.asm' % arch).split())

    debug('Injecting code to target process')
    thread, _thread_address = process.inject_code(code, 0)

    timeout = None  # Could receive timeout in millis.
    debug('Waiting for code to complete')
    thread.wait(timeout)

    # return_code = process.read_int(attach_info_address)
    # if return_code == 0:
    #     print('Attach finished successfully.')
    # else:
    #     print('Error when injecting code in target process. Error code: %s (on windows)' % (return_code,))

    process.free(thread.pInjectedMemory)
    process.free(code_address)
    process.free(attach_info_address)
    return 0


def run_python_code_linux(pid, python_code, connect_debugger_tracing=False, show_debug_info=0):
    assert '\'' not in python_code, 'Having a single quote messes with our command.'
    filedir = os.path.dirname(__file__)

    # Valid arguments for arch are i386, i386:x86-64, i386:x64-32, i8086,
    #   i386:intel, i386:x86-64:intel, i386:x64-32:intel, i386:nacl,
    #   i386:x86-64:nacl, i386:x64-32:nacl, auto.

    if is_python_64bit():
        suffix = 'amd64'
        arch = 'i386:x86-64'
    else:
        suffix = 'x86'
        arch = 'i386'

    debug('Attaching with arch: %s' % (arch,))

    target_dll = os.path.join(filedir, 'attach_linux_%s.so' % suffix)
    target_dll = os.path.abspath(os.path.normpath(target_dll))
    if not os.path.exists(target_dll):
        raise RuntimeError('Could not find dll file to inject: %s' % target_dll)

    # Note: we currently don't support debug builds
    is_debug = 0
    # Note that the space in the beginning of each line in the multi-line is important!
    cmd = [
        'gdb',
        '--nw',  # no gui interface
        '--nh',  # no ~/.gdbinit
        '--nx',  # no .gdbinit
#         '--quiet',  # no version number on startup
        '--pid',
        str(pid),
        '--batch',
#         '--batch-silent',
    ]

    cmd.extend(["--eval-command='set scheduler-locking off'"])  # If on we'll deadlock.

    cmd.extend(["--eval-command='set architecture %s'" % arch])

    cmd.extend([
        "--eval-command='call dlopen(\"%s\", 2)'" % target_dll,
        "--eval-command='call (int)DoAttach(%s, \"%s\", %s)'" % (
            is_debug, python_code, show_debug_info)
    ])

    # print ' '.join(cmd)

    env = os.environ.copy()
    # Remove the PYTHONPATH (if gdb has a builtin Python it could fail if we
    # have the PYTHONPATH for a different python version or some forced encoding).
    env.pop('PYTHONIOENCODING', None)
    env.pop('PYTHONPATH', None)
    debug('Running: %s' % (' '.join(cmd)))
    p = subprocess.Popen(
        ' '.join(cmd),
        shell=True,
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    debug('Running gdb in target process.')
    out, err = p.communicate()
    debug('stdout: %s' % (out,))
    debug('stderr: %s' % (err,))
    return out, err


def find_helper_script(filedir, script_name):
    target_filename = os.path.join(filedir, 'linux_and_mac', script_name)
    target_filename = os.path.normpath(target_filename)
    if not os.path.exists(target_filename):
        raise RuntimeError('Could not find helper script: %s' % target_filename)

    return target_filename


def run_python_code_mac(pid, python_code, connect_debugger_tracing=False, show_debug_info=0):
    assert '\'' not in python_code, 'Having a single quote messes with our command.'
    filedir = os.path.dirname(__file__)

    # Valid arguments for arch are i386, i386:x86-64, i386:x64-32, i8086,
    #   i386:intel, i386:x86-64:intel, i386:x64-32:intel, i386:nacl,
    #   i386:x86-64:nacl, i386:x64-32:nacl, auto.

    if is_python_64bit():
        suffix = 'x86_64.dylib'
        arch = 'i386:x86-64'
    else:
        suffix = 'x86.dylib'
        arch = 'i386'

    debug('Attaching with arch: %s'% (arch,))

    target_dll = os.path.join(filedir, 'attach_%s' % suffix)
    target_dll = os.path.normpath(target_dll)
    if not os.path.exists(target_dll):
        raise RuntimeError('Could not find dll file to inject: %s' % target_dll)

    lldb_prepare_file = find_helper_script(filedir, 'lldb_prepare.py')
    # Note: we currently don't support debug builds

    is_debug = 0
    # Note that the space in the beginning of each line in the multi-line is important!
    cmd = [
        'lldb',
        '--no-lldbinit',  # Do not automatically parse any '.lldbinit' files.
        # '--attach-pid',
        # str(pid),
        # '--arch',
        # arch,
        '--script-language',
        'Python'
        #         '--batch-silent',
    ]

    cmd.extend([
        "-o 'process attach --pid %d'" % pid,
        "-o 'command script import \"%s\"'" % (lldb_prepare_file,),
        "-o 'load_lib_and_attach \"%s\" %s \"%s\" %s'" % (target_dll,
            is_debug, python_code, show_debug_info),
    ])

    cmd.extend([
        "-o 'process detach'",
        "-o 'script import os; os._exit(1)'",
    ])

    # print ' '.join(cmd)

    env = os.environ.copy()
    # Remove the PYTHONPATH (if gdb has a builtin Python it could fail if we
    # have the PYTHONPATH for a different python version or some forced encoding).
    env.pop('PYTHONIOENCODING', None)
    env.pop('PYTHONPATH', None)
    debug('Running: %s' % (' '.join(cmd)))
    p = subprocess.Popen(
        ' '.join(cmd),
        shell=True,
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        )
    debug('Running lldb in target process.')
    out, err = p.communicate()
    debug('stdout: %s' % (out,))
    debug('stderr: %s' % (err,))
    return out, err


if sys.platform == 'win32':
    run_python_code = run_python_code_windows
elif is_mac():
    run_python_code = run_python_code_mac
else:
    run_python_code = run_python_code_linux


def test():
    print('Running with: %s' % (sys.executable,))
    code = '''
import os, time, sys
print(os.getpid())
#from threading import Thread
#Thread(target=str).start()
if __name__ == '__main__':
    while True:
        time.sleep(.5)
        sys.stdout.write('.\\n')
        sys.stdout.flush()
'''

    p = subprocess.Popen([sys.executable, '-u', '-c', code])
    try:
        code = 'print("It worked!")\n'

        # Real code will be something as:
        # code = '''import sys;sys.path.append(r'X:\winappdbg-code\examples'); import imported;'''
        run_python_code(p.pid, python_code=code)

        time.sleep(3)
    finally:
        p.kill()


def main(args):
    # Otherwise, assume the first parameter is the pid and anything else is code to be executed
    # in the target process.
    pid = int(args[0])
    del args[0]
    python_code = ';'.join(args)

    # Note: on Linux the python code may not have a single quote char: '
    run_python_code(pid, python_code)


if __name__ == '__main__':
    args = sys.argv[1:]
    if not args:
        print('Expected pid and Python code to execute in target process.')
    else:
        if '--test' == args[0]:
            test()
        else:
            main(args)

