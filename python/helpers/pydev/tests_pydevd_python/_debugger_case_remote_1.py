if __name__ == '__main__':
    import subprocess
    import sys
    import os
    import _debugger_case_remote_2
    root_dirname = os.path.dirname(os.path.dirname(__file__))
    
    if root_dirname not in sys.path:
        sys.path.append(root_dirname)
        
    import pydevd
    
    print('before pydevd.settrace')
    sys.stdout.flush()
    pydevd.settrace(port=8787, patch_multiprocessing=True)
    print('after pydevd.settrace')
    sys.stdout.flush()
    f = _debugger_case_remote_2.__file__
    if f.endswith('.pyc'):
        f = f[:-1]
    print('before call')
    sys.stdout.flush()
    subprocess.check_call([sys.executable, '-u', f])
    print('after call')
    sys.stdout.flush()
