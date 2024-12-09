import os
import sys


def find_vcvarsall():
    vcvarsall = None
    if sys.version_info[:2] == (2, 7):
        from distutils import msvc9compiler
        vcvarsall = msvc9compiler.find_vcvarsall(9.0)
        if not vcvarsall:
            env = os.environ
            productdir = None
            if 'VCINSTALLDIR' in env:
                productdir = env['VCINSTALLDIR']
            else:
                appdata = os.path.join(env['APPDATA'], os.pardir)
                productdir = os.path.join(appdata, 'Local\Programs\Common\Microsoft\Visual C++ for Python\9.0\VC')
            if productdir:
                path = os.path.normpath(os.path.join(os.path.join(productdir, os.path.pardir), 'vcvarsall.bat'))
                if os.path.exists(path) and os.path.isfile(path):
                    vcvarsall = path
    else:
        vcvarsall = None
        if not vcvarsall:
            env = os.environ
            productdir = None
            if 'VCINSTALLDIR' in env:
                productdir = env['VCINSTALLDIR']
            else:
                programw6432 = env['ProgramW6432']
                productdir = os.path.join(programw6432, 'Microsoft Visual Studio\\2022\Community\VC')
            if productdir:
                path = os.path.normpath(os.path.join(os.path.join(productdir, 'Auxiliary\Build\\vcvarsall.bat')))
                if os.path.exists(path) and os.path.isfile(path):
                    vcvarsall = path
    return vcvarsall
